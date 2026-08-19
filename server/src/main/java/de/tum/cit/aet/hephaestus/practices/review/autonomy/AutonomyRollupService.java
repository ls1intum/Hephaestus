package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.dto.AreaAutonomyRollupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.AutonomyAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.dto.AutonomyRollupDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutonomyRollupService {

    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository areaRepository;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    public AutonomyRollupDTO rollup(Long workspaceId) {
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        PracticeAutonomy workspaceDefault = defaults.defaultAutonomy();

        Map<PracticeAutonomy, Integer> workspaceCounts = emptyCounts();
        Map<Long, List<Practice>> byArea = new LinkedHashMap<>();
        for (Practice practice : practiceRepository.findAllForCatalog(workspaceId)) {
            PracticeArea area = practice.getArea();
            byArea.computeIfAbsent(area == null ? null : area.getId(), key -> new ArrayList<>()).add(practice);
            PracticeAutonomy effective = AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault);
            workspaceCounts.merge(effective, 1, Integer::sum);
        }

        List<AreaAutonomyRollupDTO> areas = new ArrayList<>();
        for (PracticeArea area : areaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)) {
            areas.add(areaRollup(area, byArea.getOrDefault(area.getId(), List.of()), workspaceDefault));
        }
        List<Practice> ungrouped = byArea.get(null);
        if (ungrouped != null && !ungrouped.isEmpty()) {
            areas.add(areaRollup(null, ungrouped, workspaceDefault));
        }

        return new AutonomyRollupDTO(
            AutonomyAssignmentDTO.of(
                new EffectiveAutonomy(workspaceDefault, AutonomySource.WORKSPACE),
                workspaceDefaults.rawDefaultAutonomy(workspaceId)
            ),
            workspaceCounts,
            List.copyOf(areas)
        );
    }

    private AreaAutonomyRollupDTO areaRollup(
        @Nullable PracticeArea area,
        List<Practice> practices,
        PracticeAutonomy workspaceDefault
    ) {
        Map<PracticeAutonomy, Integer> counts = emptyCounts();
        int overridden = 0;
        for (Practice practice : practices) {
            counts.merge(AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault), 1, Integer::sum);
            if (practice.getAutonomy() != null) {
                overridden++;
            }
        }
        return new AreaAutonomyRollupDTO(
            area == null ? null : area.getSlug(),
            area == null ? null : area.getName(),
            AutonomyAssignmentDTO.of(
                AutonomyResolver.resolveArea(area, workspaceDefault),
                area == null ? null : area.getAutonomy()
            ),
            counts,
            overridden
        );
    }

    private static Map<PracticeAutonomy, Integer> emptyCounts() {
        Map<PracticeAutonomy, Integer> counts = new EnumMap<>(PracticeAutonomy.class);
        for (PracticeAutonomy autonomy : PracticeAutonomy.values()) {
            counts.put(autonomy, 0);
        }
        return counts;
    }
}
