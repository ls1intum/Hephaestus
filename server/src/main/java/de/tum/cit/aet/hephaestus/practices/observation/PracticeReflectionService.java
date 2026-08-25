package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.ObservationFeedbackBody;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOutcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.PracticeInapplicableCount;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrend;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the current developer's learner-safe, per-practice reflection read model. */
@Service
@RequiredArgsConstructor
public class PracticeReflectionService {

    /** Look-back for the reflection surface — mirrors the mentor's findings window. */
    public static final int LOOKBACK_DAYS = 90;
    /** Per-practice cap on "to work on" items — the highest-impact few, not an exhaustive log. */
    private static final int MAX_ITEMS_PER_PRACTICE = 5;
    /**
     * How many of the newest reviewed work items a standing is read off. Four, matching the trend's bundle
     * size, so both surfaces on a card answer their question from the same stretch of work. Fewer is fine:
     * a standing derived from one opportunity is thin, and the card says so through its trend support rather
     * than by withholding the standing.
     */
    private static final int STANDING_WINDOW = 4;
    /**
     * Per-opportunity weight decay, newest first. Derived, not picked: the design requirement carried over
     * from the previous iteration is that TWO problem-free work items in a row must be enough to acknowledge a
     * fixed habit. With weights {@code 1, d, d², d³} that holds exactly when {@code (1 + d) > 4·(d² + d³)},
     * i.e. {@code d < 0.5}; 0.4 takes that with margin. The consequence is symmetric and intended — the newest
     * opportunity carries the majority of the weight, so a fresh regression shows up as fast as a fresh fix.
     */
    private static final double STANDING_DECAY = 0.4;
    /** Per-practice cap on acknowledged strengths — enough to affirm without drowning the signal. */
    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;

    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final CurrentDeveloperLookup currentDeveloperLookup;
    private final ObservationVisibilityPolicy visibilityPolicy;
    private final PracticeRepository practiceRepository;
    private final WorkspaceReviewDefaultsProvider workspaceReviewDefaultsProvider;
    private final PracticeTrendService practiceTrendService;
    private final Clock clock;

    /**
     * Returns practice cards built from each target's latest review run. Observations that produced no verdict
     * do not reach this learner-facing surface, and neither does anything the caller is not cleared to see;
     * every problem that survives both is shown, worst severity first.
     */
    @Transactional(readOnly = true)
    public List<ReflectionPracticeDTO> getReflection(Long workspaceId) {
        return getReflectionSnapshot(workspaceId).cards();
    }

    /**
     * Shared evidence snapshot used by both the practice reflection and practice-area status surfaces.
     *
     * <p>Three passes, in this order and for this reason: classify every practice's observations, derive the
     * trends from what the classification kept, then build each card once with its final standing. The standing
     * rule needs the trend (only the trend carries the recent-evidence streak) and the trend needs the
     * classification, so a card cannot be built before both exist.
     *
     * <p>Assumes a caller-provided transaction — it navigates lazy {@code Observation.practice} relationships.
     */
    public ReflectionSnapshot getReflectionSnapshot(Long workspaceId) {
        Optional<Long> currentDeveloperId = currentDeveloperLookup.currentDeveloperId();
        if (currentDeveloperId.isEmpty()) {
            return ReflectionSnapshot.EMPTY;
        }
        Instant since = clock.instant().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        // No global row cap: it could silently remove complete practices. Per-practice caps are applied only
        // after every eligible latest-run finding has been grouped.
        Long developerId = currentDeveloperId.get();
        List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
            developerId,
            workspaceId,
            since,
            Pageable.unpaged()
        );
        // An observation may cite a source the learner is not cleared to be shown. The policy answers that
        // per observation, so it gates this surface before anything is grouped, counted, or trended.
        Set<UUID> visible = visibilityPolicy.permitsAll(
            workspaceId,
            recent,
            SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
        );
        List<Observation> observations = recent
            .stream()
            .filter(observation -> visible.contains(observation.getId()))
            .toList();
        Map<UUID, String> deliveredGuidance = deliveredGuidanceByObservation(
            workspaceId,
            observations.stream().map(Observation::getId).collect(Collectors.toSet())
        );

        Map<String, List<Observation>> byPractice = new LinkedHashMap<>();
        for (Observation observation : observations) {
            byPractice
                .computeIfAbsent(observation.getPractice().getSlug(), ignored -> new ArrayList<>())
                .add(observation);
        }

        Map<String, PracticeEvidence> evidenceBySlug = new LinkedHashMap<>();
        for (List<Observation> group : byPractice.values()) {
            PracticeEvidence evidence = PracticeEvidence.classify(group);
            evidenceBySlug.put(evidence.slug(), evidence);
        }
        Map<String, List<Observation>> evidenceByPractice = evidenceBySlug
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().assessed(),
                    (left, ignored) -> left,
                    LinkedHashMap::new
                )
            );

        Map<String, PracticeTrend> trends = practiceTrendService.calculatePractices(evidenceByPractice);
        Map<String, Double> standingShareByPractice = evidenceBySlug
            .values()
            .stream()
            .filter(PracticeEvidence::hasCard)
            .collect(
                Collectors.toMap(
                    PracticeEvidence::slug,
                    evidence -> standingShare(evidence, requireTrend(trends, evidence.slug())),
                    (left, ignored) -> left,
                    LinkedHashMap::new
                )
            );
        // "Eligible" is an autonomy question, not a boolean: a practice contributes to its area's coverage
        // when review is admitted for it at all. AutonomyResolver already folds in the area's and the
        // workspace's answer, so an area silenced upstream drops out with its practices.
        PracticeAutonomy workspaceDefault = workspaceReviewDefaultsProvider.forWorkspace(workspaceId).defaultAutonomy();
        List<Practice> eligiblePractices = practiceRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .filter(practice -> AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault).admitsReview())
            .toList();
        Map<String, List<String>> eligiblePracticesByArea = new LinkedHashMap<>();
        for (Practice practice : eligiblePractices) {
            PracticeArea area = practice.getArea();
            if (area != null) {
                eligiblePracticesByArea
                    .computeIfAbsent(area.getSlug(), slug -> new ArrayList<>())
                    .add(practice.getSlug());
            }
        }
        // Read once here so both area surfaces weigh the same practices the same way. Only eligible practices
        // carry a weight: one that is no longer reviewed contributes nothing to its area regardless.
        Map<String, Double> areaWeightByPractice = eligiblePractices
            .stream()
            .collect(
                Collectors.toMap(
                    Practice::getSlug,
                    Practice::getAreaWeight,
                    (left, ignored) -> left,
                    LinkedHashMap::new
                )
            );

        Map<String, Integer> inapplicableByPractice = inapplicableByPractice(developerId, workspaceId, since);
        List<ReflectionPracticeDTO> cards = cards(
            evidenceBySlug,
            eligiblePractices,
            trends,
            standingShareByPractice,
            inapplicableByPractice,
            deliveredGuidance
        );

        return new ReflectionSnapshot(
            developerId,
            cards,
            evidenceByPractice,
            eligiblePracticesByArea,
            standingShareByPractice,
            areaWeightByPractice
        );
    }

    /**
     * Every practice the developer should see, whether or not it has anything to say.
     *
     * <p>The set is the UNION of two groups, and both are needed. The eligible practices are what the
     * workspace currently watches — they belong on the surface even with nothing to report, because
     * "no observation reached this" and "the reviews ran and found nothing" are different answers to
     * "how am I doing here", and a surface that shows neither leaves the learner unable to tell them apart.
     * The practices that produced a card are added even when review is no longer admitted for them: that
     * feedback was raised and delivered, and switching a practice off does not un-say it.
     */
    private static List<ReflectionPracticeDTO> cards(
        Map<String, PracticeEvidence> evidenceBySlug,
        List<Practice> eligiblePractices,
        Map<String, PracticeTrend> trends,
        Map<String, Double> standingShareByPractice,
        Map<String, Integer> inapplicableByPractice,
        Map<UUID, String> deliveredGuidance
    ) {
        Map<String, Practice> subjects = new LinkedHashMap<>();
        eligiblePractices.forEach(practice -> subjects.put(practice.getSlug(), practice));
        evidenceBySlug.values().forEach(evidence -> subjects.putIfAbsent(evidence.slug(), evidence.practice()));

        return subjects
            .entrySet()
            .stream()
            .map(entry -> {
                PracticeEvidence evidence = evidenceBySlug.get(entry.getKey());
                Double share = standingShareByPractice.get(entry.getKey());
                return evidence != null && share != null
                    ? toCard(evidence, requireTrend(trends, entry.getKey()), deliveredGuidance, share)
                    : silentCard(entry.getValue(), evidence, inapplicableByPractice.getOrDefault(entry.getKey(), 0));
            })
            .sorted(
                Comparator.<ReflectionPracticeDTO>comparingInt(card -> standingRank(card.standing())).thenComparingInt(
                    PracticeReflectionService::worstSeverityOrdinal
                )
            )
            .toList();
    }

    /**
     * A practice with nothing to report, carrying WHICH silence it is.
     *
     * <p>{@code NO_OPPORTUNITY} outranks {@code NOT_OBSERVED} because it is the more actionable of the two: a
     * review that ran and found nothing to say is a working instrument, and collapsing it into "never looked
     * at" would make it indistinguishable from an unconfigured one. Suppressed strengths count as evidence for
     * exactly that reason — a defect-detector's silence is not a demonstrated behaviour, but it does prove the
     * detector ran.
     *
     * <p>No trend either: a direction over evidence that produced no verdict would be a claim about nothing.
     */
    private static ReflectionPracticeDTO silentCard(
        Practice practice,
        @Nullable PracticeEvidence evidence,
        int notApplicable
    ) {
        boolean exercised = notApplicable > 0 || (evidence != null && evidence.suppressedStrengths() > 0);
        PracticeArea area = practice.getArea();
        return new ReflectionPracticeDTO(
            practice.getSlug(),
            practice.getName(),
            area != null ? area.getSlug() : null,
            area != null ? area.getName() : null,
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            exercised ? ReflectionPracticeDTO.Standing.NO_OPPORTUNITY : ReflectionPracticeDTO.Standing.NOT_OBSERVED,
            List.of(),
            List.of(),
            null,
            null
        );
    }

    /**
     * One card, complete on first construction.
     *
     * <p>The trend is required, not optional: every practice that has a card has an entry in the evidence map
     * the trends were derived from, so a missing trend is a programming error rather than a state to render
     * around.
     */
    private static ReflectionPracticeDTO toCard(
        PracticeEvidence evidence,
        PracticeTrend trend,
        Map<UUID, String> deliveredGuidance,
        double standingShare
    ) {
        Practice practice = evidence.practice();
        PracticeArea area = practice.getArea();
        return new ReflectionPracticeDTO(
            practice.getSlug(),
            practice.getName(),
            area != null ? area.getSlug() : null,
            area != null ? area.getName() : null,
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            StandingScale.classify(standingShare),
            items(evidence.problems(), MAX_ITEMS_PER_PRACTICE, deliveredGuidance),
            items(evidence.strengths(), MAX_STRENGTHS_PER_PRACTICE, deliveredGuidance),
            trend.direction(),
            TrendSupportDTO.from(trend.support())
        );
    }

    /**
     * The trend of a practice that produced a card.
     *
     * <p>Never absent: the trends were derived from exactly the evidence map these practices came from,
     * so a missing entry is a programming error rather than a state to render around.
     */
    private static PracticeTrend requireTrend(Map<String, PracticeTrend> trends, String slug) {
        return Objects.requireNonNull(trends.get(slug), () -> "no trend for practice with a card: " + slug);
    }

    private static List<ReflectionItemDTO> items(
        List<Observation> observations,
        int cap,
        Map<UUID, String> deliveredGuidance
    ) {
        return observations
            .stream()
            .limit(cap)
            .map(observation -> ReflectionItemDTO.from(observation, deliveredGuidance.get(observation.getId())))
            .toList();
    }

    /**
     * How positive this practice's recent evidence was, in {@code [0,1]} — the continuous value the standing
     * label is only a rendering of.
     *
     * <p>One rule, one unit, one denominator. It replaced a pair of rules that disagreed about both: an
     * existence test over ITEMS ("any problem at all in 90 days") that could not tell one problem from fifty,
     * plus a clean-streak override over OPPORTUNITIES that could. Reading the whole
     * {@link de.tum.cit.aet.hephaestus.practices.observation.trend.OutcomeVector} of the newest
     * {@link #STANDING_WINDOW} opportunities answers both questions at once, and the recency weighting keeps
     * the property the streak existed for: a fixed habit is acknowledged within two reviews.
     *
     * <p>The area consumes this number rather than the label, so the resolution won here is not quantised away
     * one level up.
     *
     * <p>The fallback is unreachable while the reflection look-back and the trend horizon are both
     * {@link #LOOKBACK_DAYS} days: a card exists only if some observation produced a verdict, and any such
     * observation is an applicable opportunity. It mirrors what the same rule would yield from the card's own
     * items, so even the impossible case cannot contradict the rule.
     */
    private static double standingShare(PracticeEvidence evidence, PracticeTrend trend) {
        return trend
            .recentPositiveShare(STANDING_WINDOW, STANDING_DECAY)
            .orElseGet(() -> evidence.problems().isEmpty() ? 1.0 : 0.0);
    }

    /**
     * One practice's window of observations, split by what each one says about the developer.
     *
     * <p>The split happens once and feeds everything downstream — the card's two lists, the trend's evidence,
     * and the census. Deriving each of those from the raw group separately is what previously required three
     * output parameters and a provisional card.
     */
    private record PracticeEvidence(
        Practice practice,
        List<Observation> problems,
        List<Observation> strengths,
        int suppressedStrengths
    ) {
        /**
         * Splits one practice's group on {@link ObservationOutcome}, which is the only place the
         * presence × assessment matrix is read.
         *
         * <p>Every problem the practice raised is kept, worst severity first. Nothing is withheld: an earlier
         * revision suppressed single-artifact problems below a model-reported confidence floor, but that column
         * was dropped after validation found it carried no discriminating information, and the per-locus
         * corroboration meant to stand in for it cannot be satisfied — {@code recurrenceKey} hashes the
         * artifact, so a locus is single-artifact by construction. Showing the record and letting the surface
         * say how often something was seen is the honest version of that intent.
         *
         * <p>Positive evidence is partitioned rather than filtered, because a defect-detector practice's
         * incoherent strengths are not noise to discard: they still prove the detector ran, which is what
         * separates {@code NO_OPPORTUNITY} from {@code NOT_OBSERVED} for its area.
         */
        static PracticeEvidence classify(List<Observation> group) {
            Practice practice = group.get(0).getPractice();
            boolean defectDetector = practice.isDefectDetector();
            List<Observation> problems = group
                .stream()
                .filter(observation -> ObservationOutcome.of(observation).isNegative())
                .sorted(Comparator.comparingInt(PracticeReflectionService::severityOrdinal))
                .toList();
            Map<Boolean, List<Observation>> positives = group
                .stream()
                .filter(observation -> ObservationOutcome.of(observation).isPositive())
                .collect(
                    Collectors.partitioningBy(observation ->
                        ObservationOutcome.of(observation).isCoherentStrengthFor(defectDetector)
                    )
                );
            // partitioningBy always yields both keys, even when one side stays empty.
            List<Observation> coherent = Objects.requireNonNull(positives.get(true));
            List<Observation> incoherent = Objects.requireNonNull(positives.get(false));
            return new PracticeEvidence(practice, problems, coherent, incoherent.size());
        }

        String slug() {
            return practice.getSlug();
        }

        /**
         * Everything that produced a verdict, which is exactly the trend's input. Observations that produced
         * none are counted in the census instead: they are filtered out before bundling, so including them
         * here could not move a trend, and they carry nothing a learner could read.
         */
        List<Observation> assessed() {
            return Stream.concat(problems.stream(), strengths.stream()).toList();
        }

        /** Whether this practice has anything to say to the learner at all. */
        boolean hasCard() {
            return !problems.isEmpty() || !strengths.isEmpty();
        }
    }

    private Map<String, Integer> inapplicableByPractice(Long developerId, Long workspaceId, Instant since) {
        return observationRepository
            .countInapplicableByDeveloperAndWorkspace(developerId, workspaceId, since)
            .stream()
            .collect(
                Collectors.toMap(
                    PracticeInapplicableCount::getPracticeSlug,
                    count -> Math.toIntExact(count.getCount()),
                    (left, ignored) -> left,
                    LinkedHashMap::new
                )
            );
    }

    /**
     * @param standingShareByPractice the continuous standing of every practice that produced a card, keyed by
     *     slug. The area aggregates THIS rather than the cards' labels: rounding each practice to one of three
     *     labels and then averaging those would throw away the resolution the practice rule just computed, and
     *     0.79 and 0.51 would weigh the same. It stays out of {@link ReflectionPracticeDTO} on purpose — the
     *     learner-facing card carries no raw score.
     */
    public record ReflectionSnapshot(
        @Nullable Long developerId,
        List<ReflectionPracticeDTO> cards,
        Map<String, List<Observation>> evidenceByPractice,
        Map<String, List<String>> eligiblePracticesByArea,
        Map<String, Double> standingShareByPractice,
        Map<String, Double> areaWeightByPractice
    ) {
        static final ReflectionSnapshot EMPTY = new ReflectionSnapshot(
            null,
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
        );
    }

    /**
     * The lanes whose text this read model's guidance means: the ones that speak about the one observation
     * they are bound to. {@code IN_APP} is excluded because it is a message about a habit across several
     * pieces of work, so it would answer "what did you tell me about this observation" with a paragraph that
     * is explicitly not about it.
     */
    private static final List<String> FEEDBACK_CHANNELS = List.of(
        FeedbackChannel.IN_CONTEXT.name(),
        FeedbackChannel.IN_CHAT.name()
    );

    private Map<UUID, String> deliveredGuidanceByObservation(Long workspaceId, Set<UUID> observationIds) {
        if (observationIds.isEmpty()) {
            return Map.of();
        }
        return feedbackObservationRepository
            .findLatestFeedbackBodiesByObservationIds(workspaceId, observationIds, FEEDBACK_CHANNELS)
            .stream()
            .collect(Collectors.toMap(ObservationFeedbackBody::getObservationId, ObservationFeedbackBody::getBody));
    }

    /** Verdicts first and worst first, because they are what a learner can act on; silences last. */
    private static int standingRank(ReflectionPracticeDTO.Standing standing) {
        return switch (standing) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
            case NO_OPPORTUNITY -> 3;
            case NOT_OBSERVED -> 4;
        };
    }

    private static int worstSeverityOrdinal(ReflectionPracticeDTO card) {
        return card
            .toWorkOn()
            .stream()
            .mapToInt(item -> item.severity() == null ? Severity.values().length : item.severity().ordinal())
            .min()
            .orElse(Severity.values().length);
    }

    private static int severityOrdinal(Observation observation) {
        return observation.getSeverity() == null ? Severity.values().length : observation.getSeverity().ordinal();
    }
}
