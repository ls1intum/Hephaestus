package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub discussion comments.
 * <p>
 * Supports both top-level comments and nested replies.
 * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionCommentDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("body") String body,
    @JsonProperty("is_answer") boolean isAnswer,
    @JsonProperty("is_minimized") boolean isMinimized,
    @JsonProperty("minimized_reason") String minimizedReason,
    @JsonProperty("author_association") String authorAssociation,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("user") GitHubUserDTO author,
    @JsonProperty("replies") List<GitHubDiscussionCommentDTO> replies
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubDiscussionCommentDTO from a GraphQL DiscussionComment model.
     *
     * @param comment the GraphQL DiscussionComment (may be null)
     * @return GitHubDiscussionCommentDTO or null if comment is null
     */
    @Nullable
    public static GitHubDiscussionCommentDTO fromDiscussionComment(@Nullable DiscussionComment comment) {
        if (comment == null) {
            return null;
        }

        return new GitHubDiscussionCommentDTO(
            null,
            comment.getDatabaseId() != null ? comment.getDatabaseId().longValue() : null,
            comment.getId(),
            comment.getBody(),
            Boolean.TRUE.equals(comment.getIsAnswer()),
            Boolean.TRUE.equals(comment.getIsMinimized()),
            comment.getMinimizedReason(),
            convertAuthorAssociation(comment.getAuthorAssociation()),
            toInstant(comment.getCreatedAt()),
            toInstant(comment.getUpdatedAt()),
            GitHubUserDTO.fromActor(comment.getAuthor()),
            extractReplies(comment)
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static Instant toInstant(@Nullable OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }

    @Nullable
    private static String convertAuthorAssociation(
        @Nullable de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.CommentAuthorAssociation association
    ) {
        if (association == null) {
            return null;
        }
        return association.name();
    }

    private static List<GitHubDiscussionCommentDTO> extractReplies(@Nullable DiscussionComment comment) {
        if (comment == null || comment.getReplies() == null || comment.getReplies().getNodes() == null) {
            return Collections.emptyList();
        }
        return comment
            .getReplies()
            .getNodes()
            .stream()
            .map(GitHubDiscussionCommentDTO::fromDiscussionComment)
            .filter(dto -> dto != null)
            .toList();
    }
}
