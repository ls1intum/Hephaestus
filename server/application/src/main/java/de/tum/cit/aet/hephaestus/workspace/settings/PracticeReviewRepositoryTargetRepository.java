package de.tum.cit.aet.hephaestus.workspace.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Every target operation carries its explicit workspace_id tenant boundary")
public interface PracticeReviewRepositoryTargetRepository
    extends JpaRepository<PracticeReviewRepositoryTarget, PracticeReviewRepositoryTarget.Key>
{
    List<PracticeReviewRepositoryTarget> findByWorkspaceId(Long workspaceId);

    @Modifying
    @Query("DELETE FROM PracticeReviewRepositoryTarget target WHERE target.workspaceId = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
