package de.tum.cit.aet.hephaestus.integration.scm.github.pullrequestreviewcomment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.pullrequest.dto.GitHubPullRequestDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * DTO for GitHub pull_request_review_comment webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewCommentEventDTO(
        @JsonProperty("action") String action,
        @JsonProperty("comment") GitHubReviewCommentDTO comment,
        @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
        @JsonProperty("repository") GitHubRepositoryRefDTO repository,
        @JsonProperty("sender") GitHubUserDTO sender)
        implements GitHubWebhookEvent {
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
            @JsonProperty("html_url") @Nullable String htmlUrl,
            @JsonProperty("user") @Nullable GitHubUserDTO author,
            @JsonProperty("created_at") @Nullable Instant createdAt,
            @JsonProperty("updated_at") @Nullable Instant updatedAt,

            @JsonProperty("pull_request_review_id") @Nullable
            Long reviewId,

            @JsonProperty("commit_id") @Nullable String commitId,
            @JsonProperty("original_commit_id") @Nullable String originalCommitId,
            @JsonProperty("author_association") String authorAssociation,
            @JsonProperty("line") @Nullable Integer line,
            @JsonProperty("original_line") @Nullable Integer originalLine,
            @JsonProperty("start_line") @Nullable Integer startLine,
            @JsonProperty("original_start_line") @Nullable Integer originalStartLine,
            @JsonProperty("side") @Nullable String side,
            @JsonProperty("start_side") @Nullable String startSide,
            @JsonProperty("in_reply_to_id") @Nullable Long inReplyToId,
            @JsonProperty("outdated") Boolean outdated) {}
}
