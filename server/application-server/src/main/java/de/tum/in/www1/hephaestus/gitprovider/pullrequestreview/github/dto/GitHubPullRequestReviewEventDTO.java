package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub pull_request_review webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestReviewEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("review") GitHubReviewDTO review,
    @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    public GitHubEventAction.PullRequestReview actionType() {
        return GitHubEventAction.PullRequestReview.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the review within the event.
     * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubReviewDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("body") String body,
        @JsonProperty("state") String state,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("user") GitHubUserDTO author,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("commit_id") String commitId
    ) {
        // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

        /**
         * Creates a GitHubReviewDTO from a GraphQL PullRequestReview model.
         * Uses fullDatabaseId instead of databaseId to avoid integer overflow.
         */
        @Nullable
        public static GitHubReviewDTO fromPullRequestReview(@Nullable PullRequestReview review) {
            if (review == null) {
                return null;
            }
            return new GitHubReviewDTO(
                review.getFullDatabaseId() != null ? review.getFullDatabaseId().longValue() : null,
                review.getId(),
                review.getBody(),
                review.getState() != null ? review.getState().name() : "PENDING",
                uriToString(review.getUrl()),
                GitHubUserDTO.fromActor(review.getAuthor()),
                toInstant(review.getSubmittedAt()),
                review.getCommit() != null ? review.getCommit().getOid() : null
            );
        }

        @Nullable
        private static String uriToString(@Nullable URI uri) {
            return uri != null ? uri.toString() : null;
        }

        @Nullable
        private static Instant toInstant(@Nullable OffsetDateTime dateTime) {
            return dateTime != null ? dateTime.toInstant() : null;
        }
    }
}
