package de.tum.cit.aet.hephaestus.agent.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ScriptUsageRepository extends Repository<ScriptUsage, ScriptUsage.Id> {
    @Query("SELECT u FROM ScriptUsage u, ReviewJob j WHERE j.workspaceId = :workspaceId"
            + " AND j.reviewId = :reviewId AND u.workspaceId = :workspaceId AND u.id.jobId = j.id")
    List<ScriptUsage> findForReview(@Param("workspaceId") long workspaceId, @Param("reviewId") UUID reviewId);

    @Query("SELECT u FROM ScriptUsage u WHERE u.workspaceId = :workspaceId AND u.id.jobId = :jobId"
            + " ORDER BY u.id.attempt, u.id.modelKind, u.id.practiceId")
    List<ScriptUsage> findForJob(@Param("workspaceId") long workspaceId, @Param("jobId") UUID jobId);
}
