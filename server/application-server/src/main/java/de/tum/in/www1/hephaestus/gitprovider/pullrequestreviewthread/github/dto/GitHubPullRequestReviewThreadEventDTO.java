package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.List;

/**
 * DTO for GitHub pull_request_review_thread webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewThreadEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("thread") GitHubThreadDTO thread,
    @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.PullRequestReviewThread actionType() {
        return GitHubEventAction.PullRequestReviewThread.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the thread within the event.
     *
     * <p>Note: GitHub's pull_request_review_thread webhook does NOT provide a numeric {@code id}
     * on the thread object - only {@code node_id} and {@code comments}. Hephaestus uses the first
     * comment's database ID as the thread's primary key (see GitHubPullRequestReviewCommentSyncService).
     * Use {@link #getFirstCommentId()} to get the thread ID for database lookups.</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubThreadDTO(
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("comments") List<GitHubThreadCommentDTO> comments,
        @JsonProperty("diff_side") String diffSide,
        @JsonProperty("line") Integer line,
        @JsonProperty("start_line") Integer startLine,
        @JsonProperty("path") String path
    ) {
        /**
         * Gets the ID of the first comment in the thread, which serves as the thread's
         * primary key in Hephaestus. This matches how threads are stored during sync
         * (see GitHubPullRequestReviewCommentSyncService lines 608-614).
         *
         * @return the first comment's ID, or null if no comments exist
         */
        public Long getFirstCommentId() {
            if (comments == null || comments.isEmpty()) {
                return null;
            }
            return comments.get(0).id();
        }
    }

    /**
     * DTO for a comment within the thread. We only need the ID field for thread identification.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubThreadCommentDTO(
        @JsonProperty("id") Long id
    ) {}
}
