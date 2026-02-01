package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;

/**
 * DTO for GitHub pull_request_review_comment webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewCommentEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("comment") GitHubReviewCommentDTO comment,
    @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.PullRequestReviewComment actionType() {
        return GitHubEventAction.PullRequestReviewComment.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the review comment within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubReviewCommentDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("diff_hunk") String diffHunk,
        @JsonProperty("path") String path,
        @JsonProperty("body") String body,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("user") GitHubUserDTO author,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("pull_request_review_id") Long reviewId,
        @JsonProperty("commit_id") String commitId,
        @JsonProperty("original_commit_id") String originalCommitId,
        @JsonProperty("author_association") String authorAssociation,
        @JsonProperty("line") Integer line,
        @JsonProperty("original_line") Integer originalLine,
        @JsonProperty("start_line") Integer startLine,
        @JsonProperty("original_start_line") Integer originalStartLine,
        @JsonProperty("side") String side,
        @JsonProperty("start_side") String startSide,
        @JsonProperty("in_reply_to_id") Long inReplyToId
    ) {}
}
