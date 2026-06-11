package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Submission request for {@link de.tum.cit.aet.hephaestus.agent.AgentJobType#ISSUE_REVIEW} jobs.
 *
 * <p>Carries the resolved, async-safe issue fields the handler needs to build job metadata. Unlike a
 * PR there is no head SHA / branch pair — the issue is identified by {@code (repository, number)}.
 * {@code updatedAt} plays the role the head SHA plays for a PR: it is the disposable freshness segment
 * of the idempotency key (an edited issue re-reviews; cooldown scopes per-issue).
 *
 * @param issueId            DB id of the issue
 * @param issueNumber        issue number within the repository
 * @param repositoryId       DB id of the repository
 * @param repositoryFullName {@code owner/repo}
 * @param title              issue title
 * @param body               issue body (may be empty)
 * @param state              issue state name (OPEN/CLOSED)
 * @param updatedAt          issue last-update timestamp (freshness segment; may be null)
 */
public record IssueReviewSubmissionRequest(
    long issueId,
    int issueNumber,
    long repositoryId,
    String repositoryFullName,
    String title,
    String body,
    String state,
    @Nullable Instant updatedAt
) implements JobSubmissionRequest {
    public IssueReviewSubmissionRequest {
        Objects.requireNonNull(repositoryFullName, "repositoryFullName must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("repositoryFullName must not be blank");
        }
        if (issueNumber <= 0) {
            throw new IllegalArgumentException("issueNumber must be positive, got " + issueNumber);
        }
    }
}
