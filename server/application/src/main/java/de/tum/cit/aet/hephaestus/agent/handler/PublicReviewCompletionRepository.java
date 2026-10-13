package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Explicit, job-scoped access only: no listing, so no queue or admin read can carry the review prose. */
public interface PublicReviewCompletionRepository extends Repository<PublicReviewCompletion, UUID> {

    PublicReviewCompletion save(PublicReviewCompletion completion);

    Optional<PublicReviewCompletion> findByWorkspaceIdAndJobId(long workspaceId, UUID jobId);

    boolean existsByWorkspaceIdAndJobId(long workspaceId, UUID jobId);

    /**
     * Clears the review prose of a bounded batch of completions whose job ended before {@code cutoff}, as the job's
     * own payload retention strips its output. The identity and digest stay, so the review is never written again.
     */
    @WorkspaceAgnostic("Cross-workspace retention sweep over job-scoped rows")
    @Modifying
    @Query(value = """
            UPDATE public_review_completion SET review = NULL WHERE job_id IN (
              SELECT c.job_id FROM public_review_completion c
              JOIN agent_job j ON j.workspace_id = c.workspace_id AND j.id = c.job_id
              WHERE c.review IS NOT NULL AND j.status NOT IN ('QUEUED', 'RUNNING') AND j.completed_at < :cutoff
              LIMIT :limit)
            """, nativeQuery = true)
    int stripEndedReviews(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
