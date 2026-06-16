package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for workspace-scoped practice areas (the configurable learning-objective grouping over
 * practices).
 */
@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface PracticeAreaRepository extends JpaRepository<PracticeArea, Long> {
    List<PracticeArea> findByWorkspaceIdOrderByDisplayOrderAscNameAsc(Long workspaceId);

    List<PracticeArea> findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long workspaceId);

    Optional<PracticeArea> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Deletes all areas for the workspace. Practices' {@code practice_area_id} is SET NULL by the FK. */
    @Modifying
    @Transactional
    @Query("DELETE FROM PracticeArea g WHERE g.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
