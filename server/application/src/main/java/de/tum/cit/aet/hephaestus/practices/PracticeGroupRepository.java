package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@WorkspaceAgnostic(
        "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save")
public interface PracticeGroupRepository extends JpaRepository<PracticeGroup, Long> {
    List<PracticeGroup> findByWorkspaceIdOrderByDisplayOrderAscNameAsc(Long workspaceId);

    List<PracticeGroup> findByWorkspaceIdAndVisibleInPracticeDashboardsTrueOrderByDisplayOrderAscNameAsc(
            Long workspaceId);

    Optional<PracticeGroup> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    @Query("SELECT COALESCE(MAX(a.displayOrder), -1) FROM PracticeGroup a WHERE a.workspace.id = :workspaceId")
    int findMaxDisplayOrder(@Param("workspaceId") Long workspaceId);

    /** Deletes all groups for the workspace. Practices' {@code practice_group_id} is SET NULL by the FK. */
    @Modifying
    @Transactional
    @Query("DELETE FROM PracticeGroup g WHERE g.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
