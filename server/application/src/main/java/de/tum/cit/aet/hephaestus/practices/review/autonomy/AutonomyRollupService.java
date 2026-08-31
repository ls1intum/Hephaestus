package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import de.tum.cit.aet.hephaestus.practices.PracticeGroupRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.dto.AutonomyAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.dto.AutonomyRollupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.GroupAutonomyRollupDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
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
    private final PracticeGroupRepository groupRepository;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    public AutonomyRollupDTO rollup(Long workspaceId) {
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        PracticeAutonomy workspaceDefault = defaults.defaultAutonomy();

        Map<PracticeAutonomy, Integer> workspaceCounts = emptyCounts();
        Map<Long, List<Practice>> byGroup = new LinkedHashMap<>();
        for (Practice practice : practiceRepository.findAllForCatalog(workspaceId)) {
            PracticeGroup group = practice.getGroup();
            byGroup.computeIfAbsent(group == null ? null : group.getId(), key -> new ArrayList<>())
                    .add(practice);
            PracticeAutonomy effective = AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault);
            workspaceCounts.merge(effective, 1, Integer::sum);
        }

        List<GroupAutonomyRollupDTO> groups = new ArrayList<>();
        for (PracticeGroup group : groupRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)) {
            groups.add(groupRollup(group, byGroup.getOrDefault(group.getId(), List.of()), workspaceDefault));
        }
        List<Practice> ungrouped = byGroup.get(null);
        if (ungrouped != null && !ungrouped.isEmpty()) {
            groups.add(groupRollup(null, ungrouped, workspaceDefault));
        }

        return new AutonomyRollupDTO(
                AutonomyAssignmentDTO.of(
                        new EffectiveAutonomy(workspaceDefault, AutonomySource.WORKSPACE),
                        workspaceDefaults.rawDefaultAutonomy(workspaceId)),
                workspaceCounts,
                List.copyOf(groups));
    }

    private GroupAutonomyRollupDTO groupRollup(
            @Nullable PracticeGroup group, List<Practice> practices, PracticeAutonomy workspaceDefault) {
        Map<PracticeAutonomy, Integer> counts = emptyCounts();
        int overridden = 0;
        for (Practice practice : practices) {
            counts.merge(AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault), 1, Integer::sum);
            if (practice.getAutonomy() != null) {
                overridden++;
            }
        }
        return new GroupAutonomyRollupDTO(
                group == null ? null : group.getSlug(),
                group == null ? null : group.getName(),
                AutonomyAssignmentDTO.of(
                        AutonomyResolver.resolveGroup(group, workspaceDefault),
                        group == null ? null : group.getAutonomy()),
                counts,
                overridden);
    }

    private static Map<PracticeAutonomy, Integer> emptyCounts() {
        Map<PracticeAutonomy, Integer> counts = new EnumMap<>(PracticeAutonomy.class);
        for (PracticeAutonomy autonomy : PracticeAutonomy.values()) {
            counts.put(autonomy, 0);
        }
        return counts;
    }
}
