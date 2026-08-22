package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.ObservationFeedbackBody;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrend;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Consecutive problem-free reviewed work items that make a practice read as a strength again, however
     * long its earlier record in the window is. Two, not one: a single clean review is routinely just a work
     * item that barely touched the practice, while two in a row is the smallest streak that is hard to get by
     * accident.
     */
    private static final int CLEAN_OPPORTUNITIES_FOR_STRENGTH = 2;
    /** Per-practice cap on acknowledged strengths — enough to affirm without drowning the signal. */
    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;

    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final UserRepository userRepository;
    private final ObservationVisibilityPolicy visibilityPolicy;
    private final PracticeRepository practiceRepository;
    private final WorkspaceReviewDefaultsProvider workspaceReviewDefaultsProvider;
    private final PracticeTrendService practiceTrendService;
    private final Clock clock;

    /**
     * Returns practice cards built from each target's latest review run. {@code NOT_APPLICABLE} findings and
     * uncorroborated low-confidence gaps do not reach this learner-facing surface.
     */
    @Transactional(readOnly = true)
    public List<ReflectionPracticeDTO> getReflection(Long workspaceId) {
        return getReflectionSnapshot(workspaceId).cards();
    }

    /** Shared evidence snapshot used by both the practice reflection and practice-area status surfaces. */
    public ReflectionSnapshot getReflectionSnapshot(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ReflectionSnapshot.EMPTY;
        }
        Instant since = clock.instant().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        // No global row cap: it could silently remove complete practices. Per-practice caps are applied only
        // after every eligible latest-run finding has been grouped.
        Long developerId = currentUser.get().getId();
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

        List<ReflectionPracticeDTO> cards = new ArrayList<>();
        Map<String, List<Observation>> evidenceByPractice = new LinkedHashMap<>();
        Map<String, EvidenceCensus> censusByPractice = new LinkedHashMap<>();
        for (List<Observation> group : byPractice.values()) {
            addPracticeCard(group, deliveredGuidance, cards, evidenceByPractice, censusByPractice);
        }

        Map<String, PracticeTrend> trends = practiceTrendService.calculatePractices(evidenceByPractice);
        cards = cards
            .stream()
            .map(card -> withTrend(card, trends.get(card.slug())))
            .collect(Collectors.toCollection(ArrayList::new));
        cards.sort(
            Comparator.<ReflectionPracticeDTO>comparingInt(card -> standingRank(card.standing())).thenComparingInt(
                PracticeReflectionService::worstSeverityOrdinal
            )
        );
        // "Eligible" is an autonomy question, not a boolean: a practice contributes to its area's coverage
        // when review is admitted for it at all. AutonomyResolver already folds in the area's and the
        // workspace's answer, so an area silenced upstream drops out with its practices.
        PracticeAutonomy workspaceDefault = workspaceReviewDefaultsProvider.forWorkspace(workspaceId).defaultAutonomy();
        Map<String, List<String>> eligiblePracticesByArea = practiceRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .filter(
                practice ->
                    practice.getArea() != null &&
                    AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault).admitsReview()
            )
            .collect(
                Collectors.groupingBy(
                    practice -> practice.getArea().getSlug(),
                    LinkedHashMap::new,
                    Collectors.mapping(Practice::getSlug, Collectors.toList())
                )
            );
        return new ReflectionSnapshot(
            developerId,
            cards,
            evidenceByPractice,
            eligiblePracticesByArea,
            censusByPractice
        );
    }

    private static void addPracticeCard(
        List<Observation> group,
        Map<UUID, String> deliveredGuidance,
        List<ReflectionPracticeDTO> cards,
        Map<String, List<Observation>> evidenceByPractice,
        Map<String, EvidenceCensus> censusByPractice
    ) {
        Practice practice = group.get(0).getPractice();
        List<Observation> visibleBad = visibleProblems(group);
        List<Observation> allGood = group
            .stream()
            .filter(observation -> observation.getAssessment() == Assessment.GOOD)
            .toList();
        // A defect-detector practice hunts an undesirable behaviour, so a PRESENT, GOOD row is incoherent —
        // what would be present is the defect — and must not surface as a false strength. Its ABSENT, GOOD
        // rows are the opposite case and belong here: the harmful behaviour could have appeared in the corpus
        // the practice bounds and did not, proven against the search the observation carries.
        List<Observation> visibleStrengths = allGood
            .stream()
            .filter(observation -> !practice.isDefectDetector() || observation.getPresence() == Presence.ABSENT)
            .toList();
        List<Observation> notApplicable = group
            .stream()
            .filter(observation -> observation.getAssessment() == null)
            .toList();

        censusByPractice.merge(
            practice.getSlug(),
            new EvidenceCensus(
                visibleBad.size() + visibleStrengths.size(),
                notApplicable.size(),
                allGood.size() - visibleStrengths.size()
            ),
            EvidenceCensus::plus
        );

        // Inapplicable observations never create a reflection card, but they remain part of the
        // shared evidence snapshot so trend coverage can distinguish "not assessed" from absent data.
        List<Observation> practiceEvidence = Stream.concat(
            Stream.concat(visibleBad.stream(), visibleStrengths.stream()),
            notApplicable.stream()
        ).toList();
        evidenceByPractice.put(practice.getSlug(), practiceEvidence);

        List<ReflectionItemDTO> toWorkOn = visibleBad
            .stream()
            .limit(MAX_ITEMS_PER_PRACTICE)
            .map(observation -> ReflectionItemDTO.from(observation, deliveredGuidance.get(observation.getId())))
            .toList();
        List<ReflectionItemDTO> strengths = visibleStrengths
            .stream()
            .limit(MAX_STRENGTHS_PER_PRACTICE)
            .map(observation -> ReflectionItemDTO.from(observation, deliveredGuidance.get(observation.getId())))
            .toList();
        if (toWorkOn.isEmpty() && strengths.isEmpty()) {
            return;
        }

        // Provisional: the whole-window rule. `withTrend` re-derives it once the recent-evidence streak is
        // known, which is the only place that can see it.
        ReflectionPracticeDTO.Standing standing = standing(toWorkOn, strengths, null);
        PracticeArea area = practice.getArea();
        cards.add(
            new ReflectionPracticeDTO(
                practice.getSlug(),
                practice.getName(),
                area != null ? area.getSlug() : null,
                area != null ? area.getName() : null,
                practice.getWhyItMatters(),
                practice.getWhatGoodLooksLike(),
                standing,
                toWorkOn,
                strengths,
                null,
                null
            )
        );
    }

    /**
     * Attaches the trend AND re-derives the standing from it — the trend result is the only carrier of the
     * recent-evidence streak the standing rule needs.
     */
    private static ReflectionPracticeDTO withTrend(ReflectionPracticeDTO card, @Nullable PracticeTrend trend) {
        return new ReflectionPracticeDTO(
            card.slug(),
            card.name(),
            card.areaSlug(),
            card.areaName(),
            card.whyItMatters(),
            card.whatGoodLooksLike(),
            standing(card.toWorkOn(), card.strengths(), trend),
            card.toWorkOn(),
            card.strengths(),
            trend == null ? null : trend.direction(),
            trend == null ? null : TrendSupportDTO.from(trend.support())
        );
    }

    /**
     * Every problem the practice raised, worst severity first. Nothing is withheld: an earlier revision
     * suppressed single-artifact problems below a model-reported confidence floor, but that column was
     * dropped after validation found it carried no discriminating information, and the per-locus
     * corroboration meant to stand in for it cannot be satisfied — {@code recurrenceKey} hashes the
     * artifact, so a locus is single-artifact by construction. Showing the record and letting the surface
     * say how often something was seen is the honest version of that intent.
     */
    private static List<Observation> visibleProblems(List<Observation> group) {
        return group
            .stream()
            .filter(observation -> observation.getAssessment() == Assessment.BAD)
            .sorted(Comparator.comparingInt(PracticeReflectionService::severityOrdinal))
            .toList();
    }

    /**
     * A practice's standing, with recent evidence outranking the whole-window record.
     *
     * <p>A clean streak wins: once the newest {@link #CLEAN_OPPORTUNITIES_FOR_STRENGTH} reviewed work items
     * that could exercise this practice carried no problem, it reads as a strength even though older reviews
     * in the window did. The whole-window rule alone made green a 90-day clean sheet, so a developer who
     * FIXED a habit stayed amber for three months and the surface never acknowledged the fix — the opposite
     * of what a formative surface is for.
     *
     * <p>The streak is opportunity-indexed (see {@link PracticeTrend#trailingCleanOpportunities()}), so it is
     * deliberately independent of calendar days: two clean reviews on one busy afternoon count exactly like
     * two clean reviews a fortnight apart. Only visible evidence feeds it — a quarantined problem was never
     * shown to the developer, so it cannot secretly hold the standing back either.
     */
    private static ReflectionPracticeDTO.Standing standing(
        List<ReflectionItemDTO> toWorkOn,
        List<ReflectionItemDTO> strengths,
        @Nullable PracticeTrend trend
    ) {
        if (trend != null && trend.trailingCleanOpportunities() >= CLEAN_OPPORTUNITIES_FOR_STRENGTH) {
            return ReflectionPracticeDTO.Standing.STRENGTH;
        }
        if (!toWorkOn.isEmpty() && !strengths.isEmpty()) {
            return ReflectionPracticeDTO.Standing.MIXED;
        }
        if (!toWorkOn.isEmpty()) {
            return ReflectionPracticeDTO.Standing.DEVELOPING;
        }
        return ReflectionPracticeDTO.Standing.STRENGTH;
    }

    public record ReflectionSnapshot(
        @Nullable Long developerId,
        List<ReflectionPracticeDTO> cards,
        Map<String, List<Observation>> evidenceByPractice,
        Map<String, List<String>> eligiblePracticesByArea,
        Map<String, EvidenceCensus> censusByPractice
    ) {
        static final ReflectionSnapshot EMPTY = new ReflectionSnapshot(null, List.of(), Map.of(), Map.of(), Map.of());
    }

    /**
     * Why a practice produced the card it did — the input a no-verdict area status needs to say WHICH kind of
     * silence it is.
     *
     * <p>Without this, "no observation at all", "the work offered no opportunity", and "we saw problems but
     * none confident enough to report" all collapse into one indistinguishable empty state. That is the same
     * conflation the trend surface removes by separating {@code STABLE} from {@code INSUFFICIENT_EVIDENCE}.
     *
     * @param displayable applicable findings that reached the learner surface
     * @param notApplicable findings where the practice had no opportunity in the reviewed work
     * @param quarantined applicable problems withheld by the confidence/corroboration floor
     * @param suppressedStrengths clean runs of a defect-detector practice — the detector applied and raised
     *     nothing. Deliberately not a strength (a detector's silence is not a demonstrated behaviour), but it
     *     is still evidence that the practice was exercised, so it must not read as "never observed".
     */
    public record EvidenceCensus(int displayable, int notApplicable, int suppressedStrengths) {
        public static final EvidenceCensus NONE = new EvidenceCensus(0, 0, 0);

        EvidenceCensus plus(EvidenceCensus other) {
            return new EvidenceCensus(
                displayable + other.displayable,
                notApplicable + other.notApplicable,
                suppressedStrengths + other.suppressedStrengths
            );
        }

        /** Any observation reached this practice, whether or not it produced a card. */
        public boolean hasAnyEvidence() {
            return displayable > 0 || notApplicable > 0 || suppressedStrengths > 0;
        }
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

    private static int standingRank(ReflectionPracticeDTO.Standing standing) {
        return switch (standing) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
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
