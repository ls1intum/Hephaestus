package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub issue_comment webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueCommentEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("comment") GitHubCommentDTO comment,
    @JsonProperty("issue") GitHubIssueDTO issue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.IssueComment actionType() {
        return GitHubEventAction.IssueComment.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the comment within the event.
     * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubCommentDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("body") String body,
        @JsonProperty("user") GitHubUserDTO author,
        @JsonProperty("author_association") String authorAssociation,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
    ) {
        // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

        /**
         * Creates a GitHubCommentDTO from a GraphQL GHIssueComment model.
         * Uses fullDatabaseId instead of databaseId to avoid integer overflow (GitHub IDs exceed 32-bit).
         */
        @Nullable
        public static GitHubCommentDTO fromIssueComment(@Nullable GHIssueComment comment) {
            if (comment == null) {
                return null;
            }
            return new GitHubCommentDTO(
                comment.getFullDatabaseId() != null ? comment.getFullDatabaseId().longValue() : null,
                comment.getId(),
                uriToString(comment.getUrl()),
                comment.getBody(),
                GitHubUserDTO.fromActor(comment.getAuthor()),
                comment.getAuthorAssociation() != null ? comment.getAuthorAssociation().name() : null,
                toInstant(comment.getCreatedAt()),
                toInstant(comment.getUpdatedAt())
            );
        }
    }
}
