package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.dto.FeedbackSourceCountDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the current developer's qualitative standing for every active practice area. */
@Service
@RequiredArgsConstructor
public class PracticeAreaStatusService {

    /** Evidence cap on the area-status surface — enough to make the status inspectable, not an exhaustive log. */
    private static final int MAX_AREA_EVIDENCE_ITEMS = 5;

    private final PracticeReflectionService practiceReflectionService;
    private final ObjectProvider<AreaGuidanceProvider> areaGuidanceProviders;
    private final Clock clock;

    /**
     * Derives one status per requested area from the same learner-safe reflection snapshot used by the
     * per-practice surface. Findings are loaded once for the workspace and partitioned in memory, avoiding
     * one observation query per card.
     *
     * <p>{@code NO_DATA} means no practice in the area has a displayable finding for the caller, including a
     * caller who is not yet a synced developer.
     */
    @Transactional(readOnly = true)
    public List<PracticeAreaStatusDTO> getAreaStatuses(Long workspaceId, List<PracticeArea> areas) {
        PracticeReflectionService.ReflectionSnapshot snapshot = practiceReflectionService.getReflectionSnapshot(
            workspaceId
        );
        Map<String, List<ReflectionPracticeDTO>> cardsByArea = cardsByArea(snapshot.cards());
        Map<String, PracticeTrajectory> practiceTrajectories = PracticeTrajectoryCalculator.calculate(
            snapshot.evidenceByPractice()
        );
        Map<String, AreaSignal> signalsByArea = areaSignals(snapshot.evidenceByPractice(), practiceTrajectories);
        Map<String, AreaGuidanceProvider.AreaGuidance> guidanceByArea = aggregatedGuidance(
            workspaceId,
            snapshot.developerId(),
            areas
        );

        return areas
            .stream()
            .map(area ->
                toAreaStatus(
                    area,
                    cardsByArea.getOrDefault(area.getSlug(), List.of()),
                    signalsByArea.getOrDefault(area.getSlug(), AreaSignal.NONE),
                    guidanceByArea.get(area.getSlug())
                )
            )
            .toList();
    }

    private static Map<String, List<ReflectionPracticeDTO>> cardsByArea(List<ReflectionPracticeDTO> cards) {
        Map<String, List<ReflectionPracticeDTO>> cardsByArea = new LinkedHashMap<>();
        for (ReflectionPracticeDTO card : cards) {
            if (card.areaSlug() != null) {
                cardsByArea.computeIfAbsent(card.areaSlug(), ignored -> new ArrayList<>()).add(card);
            }
        }
        return cardsByArea;
    }

    /**
     * Aggregated guidance overrides the deterministic fallback when a provider has material for an area.
     * A provider may intentionally cover only some areas; missing entries continue to use the rule-based text.
     */
    private Map<String, AreaGuidanceProvider.AreaGuidance> aggregatedGuidance(
        Long workspaceId,
        @Nullable Long developerId,
        List<PracticeArea> areas
    ) {
        AreaGuidanceProvider provider = areaGuidanceProviders.getIfAvailable();
        if (provider == null || developerId == null) {
            return Map.of();
        }
        return provider.findGuidance(workspaceId, developerId, areas.stream().map(PracticeArea::getSlug).toList());
    }

    private static PracticeAreaStatusDTO toAreaStatus(
        PracticeArea area,
        List<ReflectionPracticeDTO> cards,
        AreaSignal signal,
        AreaGuidanceProvider.@Nullable AreaGuidance aggregatedGuidance
    ) {
        boolean hasProblems = cards.stream().anyMatch(card -> !card.toWorkOn().isEmpty());
        boolean hasStrengths = cards.stream().anyMatch(card -> !card.strengths().isEmpty());
        PracticeAreaStatusDTO.AreaStatus status = areaStatus(hasProblems, hasStrengths);
        boolean hasDisplayableData = status != PracticeAreaStatusDTO.AreaStatus.NO_DATA;

        String guidance = null;
        PracticeAreaStatusDTO.GuidanceSource guidanceSource = null;
        if (hasDisplayableData) {
            if (aggregatedGuidance != null) {
                guidance = aggregatedGuidance.text();
                guidanceSource = aggregatedGuidance.source();
            } else {
                guidance = DeterministicAreaGuidanceComposer.compose(status, cards);
                guidanceSource = PracticeAreaStatusDTO.GuidanceSource.RULE_BASED;
            }
        }

        return new PracticeAreaStatusDTO(
            area.getSlug(),
            area.getName(),
            status,
            guidance,
            guidanceSource,
            hasDisplayableData ? signal.trajectory() : null,
            hasDisplayableData ? signal.feedbackSpanDays() : null,
            hasDisplayableData ? signal.feedbackSince() : null,
            areaEvidence(cards, hasProblems, hasStrengths),
            hasDisplayableData ? signal.sources() : List.of()
        );
    }

    private static PracticeAreaStatusDTO.AreaStatus areaStatus(boolean hasProblems, boolean hasStrengths) {
        if (hasProblems && hasStrengths) {
            return PracticeAreaStatusDTO.AreaStatus.MIXED;
        }
        if (hasProblems) {
            return PracticeAreaStatusDTO.AreaStatus.DEVELOPING;
        }
        if (hasStrengths) {
            return PracticeAreaStatusDTO.AreaStatus.STRENGTH;
        }
        return PracticeAreaStatusDTO.AreaStatus.NO_DATA;
    }

    /**
     * Problems lead because they explain the action-oriented status. A mixed status always reserves one
     * of the five evidence slots for a strength, so the payload cannot say MIXED while showing only one side.
     */
    private static List<ReflectionItemDTO> areaEvidence(
        List<ReflectionPracticeDTO> cards,
        boolean hasProblems,
        boolean hasStrengths
    ) {
        List<ReflectionItemDTO> problems = cards
            .stream()
            .flatMap(card -> card.toWorkOn().stream())
            .toList();
        List<ReflectionItemDTO> strengths = cards
            .stream()
            .flatMap(card -> card.strengths().stream())
            .toList();
        if (hasProblems && hasStrengths && problems.size() >= MAX_AREA_EVIDENCE_ITEMS) {
            return Stream.concat(
                problems.stream().limit(MAX_AREA_EVIDENCE_ITEMS - 1L),
                strengths.stream().limit(1)
            ).toList();
        }
        return Stream.concat(problems.stream(), strengths.stream()).limit(MAX_AREA_EVIDENCE_ITEMS).toList();
    }

    /** Area-level direction and provenance, all derived from the same learner-visible evidence as its card. */
    private record AreaSignal(
        @Nullable AreaTrajectory trajectory,
        @Nullable Integer feedbackSpanDays,
        @Nullable Instant feedbackSince,
        List<FeedbackSourceCountDTO> sources
    ) {
        private static final AreaSignal NONE = new AreaSignal(null, null, null, List.of());
    }

    /**
     * Aggregates practice trajectories with equal default weights and derives feedback span/source counts
     * from the observations that survived the reflection surface's learner-safety filters. A later
     * admin-configurable weight can be passed to {@link AreaTrajectoryAggregator} without changing this service.
     */
    private Map<String, AreaSignal> areaSignals(
        Map<String, List<Observation>> evidenceByPractice,
        Map<String, PracticeTrajectory> practiceTrajectories
    ) {
        Map<String, List<Observation>> evidenceByArea = new LinkedHashMap<>();
        Map<String, List<PracticeTrajectory>> trajectoriesByArea = new LinkedHashMap<>();
        for (Map.Entry<String, List<Observation>> entry : evidenceByPractice.entrySet()) {
            List<Observation> practiceEvidence = entry.getValue();
            if (practiceEvidence.isEmpty()) {
                continue;
            }
            PracticeArea area = practiceEvidence.get(0).getPractice().getArea();
            if (area == null) {
                continue;
            }
            evidenceByArea.computeIfAbsent(area.getSlug(), ignored -> new ArrayList<>()).addAll(practiceEvidence);
            PracticeTrajectory trajectory = practiceTrajectories.get(entry.getKey());
            if (trajectory != null) {
                trajectoriesByArea.computeIfAbsent(area.getSlug(), ignored -> new ArrayList<>()).add(trajectory);
            }
        }

        Instant now = clock.instant();
        Map<String, AreaSignal> signals = new HashMap<>();
        for (Map.Entry<String, List<Observation>> entry : evidenceByArea.entrySet()) {
            List<Observation> evidence = entry.getValue();
            Instant oldest = evidence.stream().map(Observation::getObservedAt).min(Instant::compareTo).orElse(null);
            Integer spanDays = oldest == null ? null : inclusiveUtcDaySpan(oldest, now);
            AreaTrajectoryAggregator.AreaTrajectorySignal trajectory = AreaTrajectoryAggregator.aggregate(
                trajectoriesByArea.getOrDefault(entry.getKey(), List.of()),
                Map.of()
            );
            signals.put(
                entry.getKey(),
                new AreaSignal(
                    trajectory == null ? null : trajectory.direction(),
                    spanDays,
                    oldest,
                    sourceCounts(evidence)
                )
            );
        }
        return signals;
    }

    private static int inclusiveUtcDaySpan(Instant oldest, Instant newest) {
        long calendarDays =
            ChronoUnit.DAYS.between(
                oldest.atZone(ZoneOffset.UTC).toLocalDate(),
                newest.atZone(ZoneOffset.UTC).toLocalDate()
            ) +
            1;
        return (int) Math.max(1, Math.min(PracticeReflectionService.LOOKBACK_DAYS, calendarDays));
    }

    private static List<FeedbackSourceCountDTO> sourceCounts(List<Observation> evidence) {
        Map<WorkArtifact, Set<Long>> artifactsByType = new EnumMap<>(WorkArtifact.class);
        for (Observation observation : evidence) {
            artifactsByType
                .computeIfAbsent(observation.getArtifactType(), ignored -> new HashSet<>())
                .add(observation.getArtifactId());
        }
        return artifactsByType
            .entrySet()
            .stream()
            .map(entry -> new FeedbackSourceCountDTO(entry.getKey(), (long) entry.getValue().size()))
            .toList();
    }
}
