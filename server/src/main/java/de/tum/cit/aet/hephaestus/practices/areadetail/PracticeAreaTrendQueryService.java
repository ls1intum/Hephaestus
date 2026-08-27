package de.tum.cit.aet.hephaestus.practices.areadetail;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStandingService;
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

/** Assembles the caller-scoped detail trend from the same developer-scoped snapshot as the area standings. */
@Service
@RequiredArgsConstructor
public class PracticeAreaTrendQueryService {

    private final PracticeAreaService practiceAreaService;
    private final PracticeStandingService standingService;
    private final PracticeTrendService trendService;

    @Transactional(readOnly = true)
    public PracticeAreaTrendDTO get(WorkspaceContext context, String areaSlug) {
        practiceAreaService.getArea(context, areaSlug);
        // Eligibility comes from the snapshot rather than a second query: the area standings and this detail
        // trend must agree on which practices count toward an area, and two derivations would drift.
        PracticeStandingService.StandingSnapshot snapshot = standingService.getStandingSnapshot(context.id());
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
        return trendService.detail(areaSlug, eligible, evidence);
    }
}
