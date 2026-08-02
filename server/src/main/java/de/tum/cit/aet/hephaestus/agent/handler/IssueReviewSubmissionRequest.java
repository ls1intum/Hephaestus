package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record IssueReviewSubmissionRequest(
    long issueId,
    int issueNumber,
    long repositoryId,
    String repositoryFullName,
    String title,
    String body,
    String state,
    @Nullable String url,
    @Nullable Instant updatedAt,
    @Nullable String triggerEvent
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
        if (issueId <= 0) {
            throw new IllegalArgumentException("issueId must be positive, got " + issueId);
        }
        if (repositoryId <= 0) {
            throw new IllegalArgumentException("repositoryId must be positive, got " + repositoryId);
        }
    }
}
