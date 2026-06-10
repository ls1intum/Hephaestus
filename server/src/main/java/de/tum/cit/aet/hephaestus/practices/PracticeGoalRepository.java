package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGoal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for workspace-scoped practice goals (the configurable learning-objective grouping over
 * practices).
 */
@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface PracticeGoalRepository extends JpaRepository<PracticeGoal, Long> {
    List<PracticeGoal> findByWorkspaceIdOrderByDisplayOrderAscNameAsc(Long workspaceId);

    List<PracticeGoal> findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long workspaceId);

    Optional<PracticeGoal> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Tenancy-scoped resolve used when binding a practice to a goal (guards cross-workspace leaks). */
    Optional<PracticeGoal> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Deletes all goals for the workspace. Practices' {@code practice_goal_id} is SET NULL by the FK. */
    @Modifying
    @Transactional
    @Query("DELETE FROM PracticeGoal g WHERE g.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
