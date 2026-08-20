package de.tum.cit.aet.hephaestus.workspace.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Every branch operation carries its explicit workspace_id tenant boundary")
public interface PracticeReviewRepositoryBranchRepository extends JpaRepository<PracticeReviewRepositoryBranch, UUID> {
    List<PracticeReviewRepositoryBranch> findByWorkspaceIdAndRepositoryTargetIdIn(
        Long workspaceId,
        Collection<UUID> repositoryTargetIds
    );

    @Modifying
    @Query("DELETE FROM PracticeReviewRepositoryBranch branch WHERE branch.workspaceId = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
