package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.ObservationAdviceBody;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
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
    static final int LOOKBACK_DAYS = 90;
    /** Per-practice cap on "to work on" items — the highest-impact few, not an exhaustive log. */
    private static final int MAX_ITEMS_PER_PRACTICE = 5;
    /** Per-practice cap on acknowledged strengths — enough to affirm without drowning the signal. */
    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;
    /** Confidence floor below which a single-target gap is hidden from the learner-facing surface. */
    private static final float QUARANTINE_CONFIDENCE = 0.5f;
    /** Distinct targets at which a low-confidence gap is corroborated enough to display. */
    private static final int CORROBORATION_TARGETS = 2;

    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final UserRepository userRepository;
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
    ReflectionSnapshot getReflectionSnapshot(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return ReflectionSnapshot.EMPTY;
        }
        Instant since = clock.instant().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        // No global row cap: it could silently remove complete practices. Per-practice caps are applied only
        // after every eligible latest-run finding has been grouped.
        Long developerId = currentUser.get().getId();
        List<Observation> observations = observationRepository.findRecentByDeveloperAndWorkspace(
            developerId,
            workspaceId,
            since,
            Pageable.unpaged()
        );
        Map<UUID, String> deliveredGuidance = deliveredGuidanceByObservation(
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
        for (List<Observation> group : byPractice.values()) {
            addPracticeCard(group, deliveredGuidance, cards, evidenceByPractice);
        }

        cards.sort(
            Comparator.<ReflectionPracticeDTO>comparingInt(card -> standingRank(card.standing())).thenComparingInt(
                PracticeReflectionService::worstSeverityOrdinal
            )
        );
        return new ReflectionSnapshot(developerId, cards, evidenceByPractice);
    }

    private static void addPracticeCard(
        List<Observation> group,
        Map<UUID, String> deliveredGuidance,
        List<ReflectionPracticeDTO> cards,
        Map<String, List<Observation>> evidenceByPractice
    ) {
        Practice practice = group.get(0).getPractice();
        List<Observation> visibleBad = visibleProblems(group);
        List<Observation> visibleStrengths = practice.isDefectDetector()
            ? List.of()
            : group
                  .stream()
                  .filter(observation -> observation.getAssessment() == Assessment.GOOD)
                  .toList();

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

        ReflectionPracticeDTO.Standing standing = standing(toWorkOn, strengths);
        List<Observation> practiceEvidence = Stream.concat(visibleBad.stream(), visibleStrengths.stream()).toList();
        evidenceByPractice.put(practice.getSlug(), practiceEvidence);
        PracticeTrajectory practiceTrajectory = PracticeTrajectoryCalculator.calculate(
            Map.of(practice.getSlug(), practiceEvidence)
        ).get(practice.getSlug());
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
                practiceTrajectory != null ? practiceTrajectory.direction() : null
            )
        );
    }

    private static List<Observation> visibleProblems(List<Observation> group) {
        List<Observation> problems = group
            .stream()
            .filter(observation -> observation.getAssessment() == Assessment.BAD)
            .toList();
        Set<Long> groupTargets = problems.stream().map(Observation::getArtifactId).collect(Collectors.toSet());
        boolean groupSingleTarget = groupTargets.size() < CORROBORATION_TARGETS;
        Map<String, Set<Long>> targetsByLocus = new HashMap<>();
        for (Observation observation : problems) {
            if (observation.getRecurrenceKey() != null) {
                targetsByLocus
                    .computeIfAbsent(observation.getRecurrenceKey(), ignored -> new HashSet<>())
                    .add(observation.getArtifactId());
            }
        }
        return problems
            .stream()
            .filter(observation ->
                !quarantined(observation, locusSingleTarget(observation, targetsByLocus, groupSingleTarget))
            )
            .sorted(Comparator.comparingDouble(PracticeReflectionService::priorityScore).reversed())
            .toList();
    }

    private static ReflectionPracticeDTO.Standing standing(
        List<ReflectionItemDTO> toWorkOn,
        List<ReflectionItemDTO> strengths
    ) {
        if (!toWorkOn.isEmpty() && !strengths.isEmpty()) {
            return ReflectionPracticeDTO.Standing.MIXED;
        }
        if (!toWorkOn.isEmpty()) {
            return ReflectionPracticeDTO.Standing.DEVELOPING;
        }
        return ReflectionPracticeDTO.Standing.STRENGTH;
    }

    record ReflectionSnapshot(
        @Nullable Long developerId,
        List<ReflectionPracticeDTO> cards,
        Map<String, List<Observation>> evidenceByPractice
    ) {
        static final ReflectionSnapshot EMPTY = new ReflectionSnapshot(null, List.of(), Map.of());
    }

    private Map<UUID, String> deliveredGuidanceByObservation(Set<UUID> observationIds) {
        if (observationIds.isEmpty()) {
            return Map.of();
        }
        return feedbackObservationRepository
            .findLatestAdviceBodiesByObservationIds(observationIds)
            .stream()
            .collect(Collectors.toMap(ObservationAdviceBody::getObservationId, ObservationAdviceBody::getBody));
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

    private static boolean quarantined(Observation observation, boolean singleTarget) {
        float confidence = observation.getConfidence() == null ? 0f : observation.getConfidence();
        return singleTarget && confidence < QUARANTINE_CONFIDENCE;
    }

    private static boolean locusSingleTarget(
        Observation observation,
        Map<String, Set<Long>> targetsByLocus,
        boolean groupSingleTarget
    ) {
        if (observation.getRecurrenceKey() == null) {
            return groupSingleTarget;
        }
        Set<Long> locusTargets = targetsByLocus.get(observation.getRecurrenceKey());
        return locusTargets == null || locusTargets.size() < CORROBORATION_TARGETS;
    }

    private static double priorityScore(Observation observation) {
        float confidence = observation.getConfidence() == null ? 0f : observation.getConfidence();
        int severityWeight =
            observation.getSeverity() == null ? 0 : Severity.values().length - observation.getSeverity().ordinal();
        return confidence * severityWeight;
    }
}
