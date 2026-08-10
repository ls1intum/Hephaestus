package de.tum.cit.aet.hephaestus.practices.review.tier;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.dto.AreaReviewTierRollupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReviewTierAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReviewTierRollupDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
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

/**
 * Counts how many practices sit at each effective tier, per area and for the workspace.
 *
 * <p>Resolved in the JVM over one catalogue read rather than aggregated in SQL. A {@code COALESCE} in the
 * query would be a second expression of the inheritance chain, and the two would agree only until the next
 * change to either — a rollup that disagrees with the rows beneath it is worse than no rollup, because it
 * is the number an administrator trusts instead of counting. A workspace's catalogue is bounded by what its
 * own admin screen lists, so one read is not the cost worth being clever about.
 *
 * <p>Every tier appears as a key even at zero, so the client renders a stable set of chips rather than a
 * row whose columns move as practices are reconfigured.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewTierRollupService {

    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository areaRepository;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    public ReviewTierRollupDTO rollup(Long workspaceId) {
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        PracticeReviewTier workspaceDefault = defaults.defaultTier();

        Map<PracticeReviewTier, Integer> workspaceCounts = emptyCounts();
        // Keyed by area id, with a null key for the practices that belong to no area at all.
        Map<Long, List<Practice>> byArea = new LinkedHashMap<>();
        for (Practice practice : practiceRepository.findAllForCatalog(workspaceId)) {
            PracticeArea area = practice.getArea();
            byArea.computeIfAbsent(area == null ? null : area.getId(), key -> new ArrayList<>()).add(practice);
            PracticeReviewTier effective = ReviewTierResolver.effectiveTierOf(practice, workspaceDefault);
            workspaceCounts.merge(effective, 1, Integer::sum);
        }

        List<AreaReviewTierRollupDTO> areas = new ArrayList<>();
        for (PracticeArea area : areaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)) {
            areas.add(areaRollup(area, byArea.getOrDefault(area.getId(), List.of()), workspaceDefault));
        }
        List<Practice> ungrouped = byArea.get(null);
        if (ungrouped != null && !ungrouped.isEmpty()) {
            areas.add(areaRollup(null, ungrouped, workspaceDefault));
        }

        return new ReviewTierRollupDTO(
            ReviewTierAssignmentDTO.of(
                new EffectiveReviewTier(workspaceDefault, ReviewTierSource.WORKSPACE),
                workspaceDefaults.rawDefaultTier(workspaceId)
            ),
            defaults.reach(),
            workspaceCounts,
            List.copyOf(areas)
        );
    }

    private AreaReviewTierRollupDTO areaRollup(
        @Nullable PracticeArea area,
        List<Practice> practices,
        PracticeReviewTier workspaceDefault
    ) {
        Map<PracticeReviewTier, Integer> counts = emptyCounts();
        int overridden = 0;
        for (Practice practice : practices) {
            counts.merge(ReviewTierResolver.effectiveTierOf(practice, workspaceDefault), 1, Integer::sum);
            if (practice.getReviewTier() != null) {
                overridden++;
            }
        }
        return new AreaReviewTierRollupDTO(
            area == null ? null : area.getSlug(),
            area == null ? null : area.getName(),
            ReviewTierAssignmentDTO.of(
                ReviewTierResolver.resolveArea(area, workspaceDefault),
                area == null ? null : area.getReviewTier()
            ),
            counts,
            overridden
        );
    }

    private static Map<PracticeReviewTier, Integer> emptyCounts() {
        Map<PracticeReviewTier, Integer> counts = new EnumMap<>(PracticeReviewTier.class);
        for (PracticeReviewTier tier : PracticeReviewTier.values()) {
            counts.put(tier, 0);
        }
        return counts;
    }
}
