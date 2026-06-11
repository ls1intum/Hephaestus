package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for workspace-scoped practice definitions.
 */
@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface PracticeRepository extends JpaRepository<Practice, Long> {
    List<Practice> findByWorkspaceIdAndActiveTrue(Long workspaceId);

    /** Active practices targeting one artifact kind — the per-job catalog filter (PR job vs issue job). */
    List<Practice> findByWorkspaceIdAndActiveTrueAndFocusArtifact(Long workspaceId, WorkArtifact focusArtifact);

    // Fetches the bound goal eagerly so PracticeDTO.from (which reads goal.slug) is safe to map
    // outside the transaction — open-in-view is disabled.
    @EntityGraph(attributePaths = "goal")
    Optional<Practice> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Practices bound to a goal (the per-goal dashboard aggregation key). */
    List<Practice> findByWorkspaceIdAndGoalId(Long workspaceId, Long goalId);

    boolean existsByWorkspaceId(Long workspaceId);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /**
     * Lists practices for a workspace with optional category and active filters.
     * Null filter values are ignored (match all).
     */
    @EntityGraph(attributePaths = "goal")
    @Query(
        """
        SELECT p FROM Practice p
        WHERE p.workspace.id = :workspaceId
        AND (:category IS NULL OR p.category = :category)
        AND (:active IS NULL OR p.active = :active)
        ORDER BY p.name ASC
        """
    )
    List<Practice> findByFilters(
        @Param("workspaceId") Long workspaceId,
        @Param("category") String category,
        @Param("active") Boolean active
    );

    /** Deletes all practices for the workspace. Cascades to practice_finding via ON DELETE CASCADE. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Practice p WHERE p.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
