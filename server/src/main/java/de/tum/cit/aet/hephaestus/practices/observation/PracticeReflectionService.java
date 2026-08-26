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

    /** Look-back for the reflection surface, matching the mentor's findings window. */
    public static final int LOOKBACK_DAYS = 90;
    /** Per-practice cap on "to work on" items: the highest-impact few, not an exhaustive log. */
    private static final int MAX_ITEMS_PER_PRACTICE = 5;
    /**
     * How many of the newest reviewed work items a standing is read off. Four, matching the trend's bundle
     * size, so both numbers on a card come from the same stretch of work. Fewer is fine: the card says how
     * thin the evidence is through its trend support rather than by withholding the standing.
     */
    private static final int STANDING_WINDOW = 4;
    /**
     * Per-opportunity weight decay, newest first. Derived rather than picked: two problem-free work items in a
     * row must be enough to acknowledge a fixed habit, which with weights {@code 1, d, d², d³} holds exactly
     * when {@code (1 + d) > 4·(d² + d³)}, i.e. {@code d < 0.5}. The effect is symmetric and intended: a fresh
     * regression shows up as fast as a fresh fix.
     */
    private static final double STANDING_DECAY = 0.4;
    /** Per-practice cap on acknowledged strengths: enough to affirm without drowning the signal. */
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
     * <p>Three passes, and the order is forced: classify the observations, derive the trends from what the
     * classification kept, then build each card once. A standing needs its trend and a trend needs the
     * classification, so a card cannot be built before both exist.
     *
     * <p>Assumes a caller-provided transaction, since it navigates lazy {@code Observation.practice}.
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
            // Verdictless rows on purpose. None are rendered, but they have to be COUNTED: "ran and found
            // nothing to judge" and "was never looked at" are different answers, and only the rows tell them
            // apart. Asking here lets one pass classify and count; the alternative was a second query whose
            // predicates had to be kept identical by hand.
            false,
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
                    entry -> entry.getValue().observed(),
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

        List<ReflectionPracticeDTO> cards = cards(
            evidenceBySlug,
            eligiblePractices,
            trends,
            standingShareByPractice,
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
     * <p>The UNION of two sets, both needed. The eligible practices are what the workspace currently watches;
     * they belong here even with nothing to report, because "no observation reached this" and "the reviews ran
     * and found nothing" are different answers a learner cannot otherwise tell apart.
     * The practices that produced a card are added even when review is no longer admitted for them: that
     * feedback was raised and delivered, and switching a practice off does not un-say it.
     */
    private static List<ReflectionPracticeDTO> cards(
        Map<String, PracticeEvidence> evidenceBySlug,
        List<Practice> eligiblePractices,
        Map<String, PracticeTrend> trends,
        Map<String, Double> standingShareByPractice,
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
                    : silentCard(entry.getValue(), evidence);
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
     * <p>{@code NO_OPPORTUNITY} outranks {@code NOT_OBSERVED}: a review that ran and found nothing to say is a
     * working instrument, not an unconfigured one. Suppressed strengths count for the same reason. A
     * defect-detector's silence is no demonstrated behaviour, but it does prove the detector ran.
     *
     * <p>No trend either: a direction over evidence that produced no verdict would be a claim about nothing.
     */
    private static ReflectionPracticeDTO silentCard(Practice practice, @Nullable PracticeEvidence evidence) {
        boolean exercised =
            evidence != null && (!evidence.withoutVerdict().isEmpty() || evidence.suppressedStrengths() > 0);
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
     * How positive this practice's recent evidence was, in {@code [0,1]}. The standing label is a rendering of
     * this number, and the level above consumes the number rather than the label.
     *
     * <p>One rule over the newest {@link #STANDING_WINDOW} opportunities, weighted by recency. It replaced a
     * pair that disagreed about both unit and denominator: an existence test over items that could not tell
     * one problem from fifty, plus a clean-streak override over opportunities that could.
     *
     * <p>The fallback is unreachable while the look-back and the trend horizon are both
     * {@link #LOOKBACK_DAYS} days, since a card exists only where some observation produced a verdict.
     */
    private static double standingShare(PracticeEvidence evidence, PracticeTrend trend) {
        return trend
            .recentPositiveShare(STANDING_WINDOW, STANDING_DECAY)
            .orElseGet(() -> evidence.problems().isEmpty() ? 1.0 : 0.0);
    }

    /**
     * One practice's window of observations, split by what each one says about the developer.
     *
     * <p>Split once, then read by everything downstream: the card's two lists, the trend's evidence, the
     * census. Deriving each separately from the raw group is what previously required three output parameters
     * and a provisional card.
     */
    private record PracticeEvidence(
        Practice practice,
        List<Observation> problems,
        List<Observation> strengths,
        int suppressedStrengths,
        List<Observation> withoutVerdict
    ) {
        /**
         * Sorts one practice's window into the buckets every downstream reader needs, reading
         * {@link ObservationOutcome} exactly once per observation.
         *
         * <p>Grouping on the outcome rather than testing it per bucket is what makes the split visibly
         * exhaustive: the five outcomes are the five keys, and every row lands under one of them. The earlier
         * shape asked {@code isNegative} / {@code isPositive} / {@code isCoherentStrengthFor} in sequence, which
         * evaluated the matrix two to four times per row and left "nothing falls through" as a claim in prose
         * rather than something the code shows.
         *
         * <p>Every problem is kept, worst severity first. Nothing is withheld: an earlier confidence floor was
         * dropped after validation found it carried no signal, and the per-locus corroboration meant to replace
         * it cannot work, since {@code recurrenceKey} hashes the artifact and a locus is therefore
         * single-artifact by construction.
         *
         * <p>A defect-detector's {@code DEMONSTRATED_STRENGTH} rows are counted, not discarded. What would be
         * demonstrated is the defect, so they are no strength to show, but they do prove the detector ran. The
         * rule is applied once to the bucket rather than once per row.
         */
        static PracticeEvidence classify(List<Observation> group) {
            Practice practice = group.get(0).getPractice();
            Map<ObservationOutcome, List<Observation>> byOutcome = group
                .stream()
                .collect(Collectors.groupingBy(ObservationOutcome::of));
            List<Observation> demonstrated = bucket(byOutcome, ObservationOutcome.DEMONSTRATED_STRENGTH);
            List<Observation> avoided = bucket(byOutcome, ObservationOutcome.SAFE_AVOIDANCE);
            boolean detectorStrengthIsIncoherent = !ObservationOutcome.DEMONSTRATED_STRENGTH.isCoherentStrengthFor(
                practice.isDefectDetector()
            );
            return new PracticeEvidence(
                practice,
                Stream.concat(
                    bucket(byOutcome, ObservationOutcome.COMMISSION_PROBLEM).stream(),
                    bucket(byOutcome, ObservationOutcome.OMISSION_GAP).stream()
                )
                    .sorted(Comparator.comparingInt(PracticeReflectionService::severityOrdinal))
                    .toList(),
                detectorStrengthIsIncoherent
                    ? avoided
                    : Stream.concat(demonstrated.stream(), avoided.stream()).toList(),
                detectorStrengthIsIncoherent ? demonstrated.size() : 0,
                bucket(byOutcome, ObservationOutcome.NOT_APPLICABLE)
            );
        }

        private static List<Observation> bucket(
            Map<ObservationOutcome, List<Observation>> byOutcome,
            ObservationOutcome outcome
        ) {
            return byOutcome.getOrDefault(outcome, List.of());
        }

        String slug() {
            return practice.getSlug();
        }

        /**
         * Everything the practice's latest runs said, verdict or not. This is the trend's input.
         *
         * <p>The verdictless rows belong here even though they can never move a direction. The bundler drops an
         * opportunity that produced no verdict at all, so including them changes no share and no posterior; what
         * it does change is that a work item the practice looked at and could not judge is visible as an
         * opportunity that yielded nothing, rather than as an absence indistinguishable from work that was never
         * reviewed. That is the same distinction {@code NO_OPPORTUNITY} draws one level up.
         */
        List<Observation> observed() {
            return Stream.concat(
                Stream.concat(problems.stream(), strengths.stream()),
                withoutVerdict.stream()
            ).toList();
        }

        /** Whether this practice has anything to say to the learner at all. */
        boolean hasCard() {
            return !problems.isEmpty() || !strengths.isEmpty();
        }
    }

    /**
     * @param standingShareByPractice the continuous standing of every practice that produced a card, keyed by
     *     slug. The level above aggregates THIS rather than the rendered labels, which would put 0.79 and 0.51
     *     at the same weight. Kept out of {@link ReflectionPracticeDTO}: the learner's card carries no score.
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
