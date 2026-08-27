package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.dto.FeedbackSourceCountDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrend;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendDirection;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the current developer's qualitative standing for every active practice area. */
@Service
@RequiredArgsConstructor
public class PracticeAreaStatusService {

    /** Evidence cap: enough to make a status inspectable, not an exhaustive log. */
    private static final int MAX_AREA_EVIDENCE_ITEMS = 5;

    private final PracticeReflectionService practiceReflectionService;
    private final PracticeTrendService practiceTrendService;
    private final Clock clock;

    /**
     * One status per requested group, rolled up from the practice standings of the same developer snapshot
     * the per-practice surface renders. Observations are loaded once and partitioned in memory rather than
     * queried per card.
     *
     * <p>With nothing displayable the status reports WHY, as {@code NO_OPPORTUNITY} or {@code NOT_OBSERVED},
     * instead of one undifferentiated empty state.
     */
    @Transactional(readOnly = true)
    public List<PracticeAreaStatusDTO> getAreaStatuses(Long workspaceId, List<PracticeArea> areas) {
        PracticeReflectionService.ReflectionSnapshot snapshot = practiceReflectionService.getReflectionSnapshot(
            workspaceId
        );
        Map<String, List<ReflectionPracticeDTO>> cardsByArea = cardsByArea(snapshot.cards());
        Map<String, PracticeTrend> practiceTrends = practiceTrendService.calculatePractices(
            snapshot.evidenceByPractice()
        );
        Map<String, AreaSignal> signalsByArea = areaSignals(
            snapshot.evidenceByPractice(),
            practiceTrends,
            snapshot.eligiblePracticesByArea()
        );
        return areas
            .stream()
            .map(area ->
                toAreaStatus(
                    area,
                    cardsByArea.getOrDefault(area.getSlug(), List.of()),
                    snapshot.standingShareByPractice(),
                    Set.copyOf(snapshot.eligiblePracticesByArea().getOrDefault(area.getSlug(), List.of())),
                    signalsByArea.getOrDefault(area.getSlug(), AreaSignal.NONE)
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

    private static PracticeAreaStatusDTO toAreaStatus(
        PracticeArea area,
        List<ReflectionPracticeDTO> cards,
        Map<String, Double> standingShareByPractice,
        Set<String> eligiblePracticeSlugs,
        AreaSignal signal
    ) {
        List<ReflectionPracticeDTO> verdicts = votingVerdicts(cards, eligiblePracticeSlugs);
        PracticeAreaStatusDTO.AreaStatus status = areaStatus(cards, verdicts, standingShareByPractice);
        boolean hasDisplayableData = PracticeAreaStatusDTO.isVerdict(status);
        // Item-level, unlike the status: the question here is which KINDS of evidence exist to show, which a
        // practice standing has already abstracted away.
        boolean hasProblems = cards.stream().anyMatch(card -> !card.toWorkOn().isEmpty());
        boolean hasStrengths = cards.stream().anyMatch(card -> !card.strengths().isEmpty());

        String guidance = hasDisplayableData ? DeterministicAreaGuidanceComposer.compose(status, verdicts) : null;
        PracticeAreaStatusDTO.GuidanceSource guidanceSource = hasDisplayableData
            ? PracticeAreaStatusDTO.GuidanceSource.RULE_BASED
            : null;

        return new PracticeAreaStatusDTO(
            area.getSlug(),
            area.getName(),
            status,
            guidance,
            guidanceSource,
            signal.direction(),
            signal.trendSupport(),
            hasDisplayableData ? signal.feedbackSpanDays() : null,
            hasDisplayableData ? signal.feedbackSince() : null,
            areaEvidence(cards, hasProblems, hasStrengths),
            hasDisplayableData ? signal.sources() : List.of()
        );
    }

    private static List<ReflectionPracticeDTO> votingVerdicts(
        List<ReflectionPracticeDTO> cards,
        Set<String> eligiblePracticeSlugs
    ) {
        return cards
            .stream()
            .filter(card -> ReflectionPracticeDTO.isVerdict(card.standing()))
            .filter(card -> eligiblePracticeSlugs.contains(card.slug()))
            .toList();
    }

    private static PracticeAreaStatusDTO.AreaStatus areaStatus(
        List<ReflectionPracticeDTO> cards,
        List<ReflectionPracticeDTO> verdicts,
        Map<String, Double> standingShareByPractice
    ) {
        if (verdicts.isEmpty()) {
            return cards.stream().anyMatch(card -> card.standing() == ReflectionPracticeDTO.Standing.NO_OPPORTUNITY)
                ? PracticeAreaStatusDTO.AreaStatus.NO_OPPORTUNITY
                : PracticeAreaStatusDTO.AreaStatus.NOT_OBSERVED;
        }
        double areaShare = verdicts
            .stream()
            .mapToDouble(card -> standingShareByPractice.getOrDefault(card.slug(), 0.0))
            .average()
            .orElseThrow();
        return switch (StandingScale.classify(areaShare)) {
            case STRENGTH -> PracticeAreaStatusDTO.AreaStatus.STRENGTH;
            case MIXED -> PracticeAreaStatusDTO.AreaStatus.MIXED;
            case DEVELOPING -> PracticeAreaStatusDTO.AreaStatus.DEVELOPING;
            case NOT_OBSERVED, NO_OPPORTUNITY -> throw new IllegalStateException(
                "StandingScale only classifies verdicts"
            );
        };
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

    /** Area-level direction and provenance, derived from the same visible evidence as its card. */
    private record AreaSignal(
        @Nullable TrendDirection direction,
        @Nullable TrendSupportDTO trendSupport,
        @Nullable Integer feedbackSpanDays,
        @Nullable Instant feedbackSince,
        List<FeedbackSourceCountDTO> sources
    ) {
        private static final AreaSignal NONE = new AreaSignal(null, null, null, null, List.of());
    }

    private Map<String, AreaSignal> areaSignals(
        Map<String, List<Observation>> evidenceByPractice,
        Map<String, PracticeTrend> practiceTrends,
        Map<String, List<String>> eligiblePracticesByArea
    ) {
        Map<String, List<Observation>> evidenceByArea = new LinkedHashMap<>();
        Map<String, List<PracticeTrend>> trendsByArea = new LinkedHashMap<>();
        Set<String> eligibleSlugs = eligiblePracticesByArea
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(Collectors.toSet());
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
            PracticeTrend practiceTrend = practiceTrends.get(entry.getKey());
            // Only an eligible practice's trend joins the group's. The evidence map stays unfiltered on
            // purpose: a practice review is no longer admitted for still has findings worth showing, and the
            // feedback span is still dated by them. Its VERDICT is what it has stopped casting, and the
            // standing beside this already excludes it.
            if (practiceTrend != null && eligibleSlugs.contains(entry.getKey())) {
                trendsByArea.computeIfAbsent(area.getSlug(), ignored -> new ArrayList<>()).add(practiceTrend);
            }
        }

        Instant now = clock.instant();
        Map<String, AreaSignal> signals = new HashMap<>();
        for (Map.Entry<String, List<Observation>> entry : evidenceByArea.entrySet()) {
            List<Observation> evidence = entry.getValue();
            Instant oldest = evidence.stream().map(Observation::getObservedAt).min(Instant::compareTo).orElse(null);
            Integer spanDays = oldest == null ? null : inclusiveUtcDaySpan(oldest, now);
            PracticeTrend direction = practiceTrendService.calculateArea(
                entry.getKey(),
                eligiblePracticesByArea.getOrDefault(entry.getKey(), List.of()),
                trendsByArea.getOrDefault(entry.getKey(), List.of())
            );
            signals.put(
                entry.getKey(),
                new AreaSignal(
                    direction.direction(),
                    TrendSupportDTO.from(direction.support()),
                    spanDays,
                    oldest,
                    sourceCounts(evidence)
                )
            );
        }
        for (Map.Entry<String, List<String>> entry : eligiblePracticesByArea.entrySet()) {
            PracticeTrend direction = practiceTrendService.calculateArea(
                entry.getKey(),
                entry.getValue(),
                trendsByArea.getOrDefault(entry.getKey(), List.of())
            );
            signals.putIfAbsent(
                entry.getKey(),
                new AreaSignal(direction.direction(), TrendSupportDTO.from(direction.support()), null, null, List.of())
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
        // Ordered by kind value, not by declaration order: ArtifactKind is an open vocabulary of
        // "<domain>.<kind>" strings, so there is no enum ordinal left to render stably against.
        Map<ArtifactKind, Set<Long>> artifactsByType = new TreeMap<>(Comparator.comparing(ArtifactKind::value));
        for (Observation observation : evidence) {
            artifactsByType
                .computeIfAbsent(observation.getArtifactKind(), ignored -> new HashSet<>())
                .add(observation.getArtifactId());
        }
        return artifactsByType
            .entrySet()
            .stream()
            .map(entry -> new FeedbackSourceCountDTO(entry.getKey(), (long) entry.getValue().size()))
            .toList();
    }
}
