package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGoal;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link PracticeGoal}s — the configurable learning-objective grouping over
 * practices — and binding practices to them.
 *
 * <p>A goal is a read-model/organizing concept: it groups practices for the dashboards and never
 * participates in detection. The 1:N binding ({@link Practice#getGoal()}) keeps the per-goal
 * acted-on/total progress denominator unambiguous.
 */
@Service
@RequiredArgsConstructor
public class PracticeGoalService {

    private static final Logger log = LoggerFactory.getLogger(PracticeGoalService.class);

    private final PracticeGoalRepository practiceGoalRepository;
    private final PracticeRepository practiceRepository;
    private final WorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public List<PracticeGoal> listGoals(WorkspaceContext ctx, @Nullable Boolean activeOnly) {
        return Boolean.TRUE.equals(activeOnly)
            ? practiceGoalRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(ctx.id())
            : practiceGoalRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(ctx.id());
    }

    /**
     * Sets each goal's {@code displayOrder} to its index in the given list — one atomic write of the
     * whole ordering, so a mid-list failure can't leave duplicate/garbled order values. Every slug must
     * belong to the workspace (a stale/foreign slug is a 404).
     */
    @Transactional
    public void reorder(WorkspaceContext ctx, List<String> orderedSlugs) {
        int order = 0;
        for (String slug : orderedSlugs) {
            PracticeGoal goal = practiceGoalRepository
                .findByWorkspaceIdAndSlug(ctx.id(), slug)
                .orElseThrow(() -> new EntityNotFoundException("PracticeGoal", slug));
            goal.setDisplayOrder(order++);
            practiceGoalRepository.save(goal);
        }
    }

    @Transactional(readOnly = true)
    public PracticeGoal getGoal(WorkspaceContext ctx, String slug) {
        return practiceGoalRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("PracticeGoal", slug));
    }

    @Transactional
    public PracticeGoal createGoal(
        WorkspaceContext ctx,
        String slug,
        String name,
        @Nullable String description,
        int displayOrder
    ) {
        if (practiceGoalRepository.existsByWorkspaceIdAndSlug(ctx.id(), slug)) {
            throw new PracticeGoalSlugConflictException(
                "A practice goal with slug '" + slug + "' already exists in this workspace."
            );
        }
        Workspace workspace = workspaceRepository
            .findById(ctx.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", ctx.slug()));

        PracticeGoal goal = new PracticeGoal();
        goal.setWorkspace(workspace);
        goal.setSlug(slug);
        goal.setName(name);
        goal.setDescription(description);
        goal.setDisplayOrder(displayOrder);

        try {
            goal = practiceGoalRepository.save(goal);
        } catch (DataIntegrityViolationException ex) {
            // Safety net for a concurrent create with the same slug.
            throw new PracticeGoalSlugConflictException(
                "A practice goal with slug '" + slug + "' already exists in this workspace.",
                ex
            );
        }
        log.info("Created practice goal '{}' (slug={}) in workspace {}", goal.getName(), goal.getSlug(), ctx.slug());
        return goal;
    }

    @Transactional
    public PracticeGoal updateGoal(
        WorkspaceContext ctx,
        String slug,
        @Nullable String name,
        @Nullable String description,
        @Nullable Integer displayOrder
    ) {
        PracticeGoal goal = getGoal(ctx, slug);
        if (name != null) {
            goal.setName(name);
        }
        if (description != null) {
            goal.setDescription(description);
        }
        if (displayOrder != null) {
            goal.setDisplayOrder(displayOrder);
        }
        return practiceGoalRepository.save(goal);
    }

    @Transactional
    public PracticeGoal setActive(WorkspaceContext ctx, String slug, boolean active) {
        PracticeGoal goal = getGoal(ctx, slug);
        goal.setActive(active);
        return practiceGoalRepository.save(goal);
    }

    /** Deletes a goal. Bound practices are unbound (their {@code practice_goal_id} is SET NULL by the FK). */
    @Transactional
    public void deleteGoal(WorkspaceContext ctx, String slug) {
        PracticeGoal goal = getGoal(ctx, slug);
        practiceGoalRepository.delete(goal);
        log.info("Deleted practice goal (slug={}) in workspace {}", slug, ctx.slug());
    }

    /**
     * Binds a practice to a goal, or unbinds it when {@code goalSlug} is {@code null}. Both the practice
     * and the goal are resolved within {@code ctx}'s workspace, so a practice can never be bound to
     * another workspace's goal.
     */
    @Transactional
    public Practice bindPractice(WorkspaceContext ctx, String practiceSlug, @Nullable String goalSlug) {
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), practiceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", practiceSlug));

        if (goalSlug == null) {
            practice.setGoal(null);
        } else {
            PracticeGoal goal = practiceGoalRepository
                .findByWorkspaceIdAndSlug(ctx.id(), goalSlug)
                .orElseThrow(() -> new EntityNotFoundException("PracticeGoal", goalSlug));
            practice.setGoal(goal);
        }
        return practiceRepository.save(practice);
    }
}
