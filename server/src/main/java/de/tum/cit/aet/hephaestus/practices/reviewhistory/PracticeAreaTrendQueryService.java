package de.tum.cit.aet.hephaestus.practices.reviewhistory;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeReflectionService;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.PracticeAreaTrendDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the caller-scoped detail trend from the same learner-safe snapshot as the profile cards. */
@Service
@RequiredArgsConstructor
public class PracticeAreaTrendQueryService {

    private final PracticeAreaService practiceAreaService;
    private final PracticeReflectionService reflectionService;
    private final PracticeTrendService trendService;

    @Transactional(readOnly = true)
    public PracticeAreaTrendDTO get(WorkspaceContext context, String areaSlug) {
        practiceAreaService.getArea(context, areaSlug);
        // Eligibility comes from the snapshot rather than a second query: the profile cards and this detail
        // trend must agree on which practices count toward an area, and two derivations would drift.
        PracticeReflectionService.ReflectionSnapshot snapshot = reflectionService.getReflectionSnapshot(context.id());
        List<String> eligible = snapshot.eligiblePracticesByArea().getOrDefault(areaSlug, List.of());
        Set<String> eligibleSet = Set.copyOf(eligible);
        Map<String, List<Observation>> evidence = snapshot
            .evidenceByPractice()
            .entrySet()
            .stream()
            .filter(entry -> eligibleSet.contains(entry.getKey()))
            .collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new)
            );
        // Weights come from the same snapshot for the same reason: this detail view and the area status must
        // not disagree about how much a practice counts.
        return trendService.detail(areaSlug, eligible, evidence, snapshot.areaWeightByPractice());
    }
}
