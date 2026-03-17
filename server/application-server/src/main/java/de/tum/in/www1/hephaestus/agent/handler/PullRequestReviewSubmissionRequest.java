package de.tum.in.www1.hephaestus.agent.handler;

import de.tum.in.www1.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import java.util.Objects;

/**
 * Submission request for {@link de.tum.in.www1.hephaestus.agent.AgentJobType#PULL_REQUEST_REVIEW}
 * jobs.
 *
 * <p>Combines the async-safe {@link EventPayload.PullRequestData} snapshot with branch
 * information not present on that DTO. The event listener (issue #746) constructs this
 * from the {@code PullRequest} entity before it detaches.
 *
 * @param pullRequest async-safe pull request snapshot (no JPA proxies)
 * @param headRefName source branch name (e.g. {@code "feature/my-feature"})
 * @param headRefOid  head commit SHA
 * @param baseRefName target branch name (e.g. {@code "main"})
 */
public record PullRequestReviewSubmissionRequest(
    EventPayload.PullRequestData pullRequest,
    String headRefName,
    String headRefOid,
    String baseRefName
) implements JobSubmissionRequest {
    public PullRequestReviewSubmissionRequest {
        Objects.requireNonNull(pullRequest, "pullRequest must not be null");
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
}
