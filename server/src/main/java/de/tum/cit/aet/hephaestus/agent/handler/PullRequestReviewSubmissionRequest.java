package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Submission request for {@link de.tum.cit.aet.hephaestus.agent.AgentJobType#PULL_REQUEST_REVIEW}
 * jobs.
 *
 * <p>Combines the async-safe {@link ScmEventPayload.PullRequestData} snapshot with branch
 * information not present on that DTO. The event listener (issue #746) constructs this
 * from the {@code PullRequest} entity before it detaches.
 *
 * @param pullRequest async-safe pull request snapshot (no JPA proxies)
 * @param headRefName source branch name (e.g. {@code "feature/my-feature"})
 * @param headRefOid  head commit SHA
 * @param baseRefName target branch name (e.g. {@code "main"})
 */
public record PullRequestReviewSubmissionRequest(
    ScmEventPayload.PullRequestData pullRequest,
    String headRefName,
    String headRefOid,
    String baseRefName,
    @Nullable String triggerEvent
) implements JobSubmissionRequest {
    public PullRequestReviewSubmissionRequest {
        Objects.requireNonNull(pullRequest, "pullRequest must not be null");
        Objects.requireNonNull(pullRequest.repository(), "pullRequest.repository() must not be null");
        Objects.requireNonNull(headRefName, "headRefName must not be null");
        Objects.requireNonNull(headRefOid, "headRefOid must not be null");
        Objects.requireNonNull(baseRefName, "baseRefName must not be null");
        if (headRefName.isBlank()) {
            throw new IllegalArgumentException("headRefName must not be blank");
        }
        if (headRefOid.isBlank()) {
            throw new IllegalArgumentException("headRefOid must not be blank");
        }
        if (baseRefName.isBlank()) {
            throw new IllegalArgumentException("baseRefName must not be blank");
        }
    }

    /**
     * Back-compat constructor for callers that do not carry a trigger event (the gate-bypass dev path
     * and the bot-command path); the job then runs the full focus-active practice set as before.
     */
    public PullRequestReviewSubmissionRequest(
        ScmEventPayload.PullRequestData pullRequest,
        String headRefName,
        String headRefOid,
        String baseRefName
    ) {
        this(pullRequest, headRefName, headRefOid, baseRefName, null);
    }
}
