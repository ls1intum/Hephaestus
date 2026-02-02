package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHCommentAuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Discussion Comments.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
 * <p>
 * Discussion comments can be top-level comments on a discussion or replies
 * to other comments (threaded). The replyToId field indicates the parent
 * comment's node ID if this is a reply.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionCommentDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("body") String body,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("is_answer") boolean isAnswer,
    @JsonProperty("is_minimized") boolean isMinimized,
    @JsonProperty("minimized_reason") String minimizedReason,
    @JsonProperty("author_association") String authorAssociation,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("user") GitHubUserDTO author,
    // For reply threading - contains the parent comment's node ID
    @JsonProperty("reply_to_id") String replyToNodeId
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     * <p>
     * Note: Unlike Discussion, DiscussionComment in GraphQL DOES have databaseId.
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
    public static GitHubDiscussionCommentDTO fromDiscussionComment(@Nullable GHDiscussionComment comment) {
        if (comment == null) {
            return null;
        }

        return new GitHubDiscussionCommentDTO(
            null,
            comment.getDatabaseId() != null ? comment.getDatabaseId().longValue() : null,
            comment.getId(),
            comment.getBody(),
            uriToString(comment.getUrl()),
            comment.getIsAnswer(),
            comment.getIsMinimized(),
            comment.getMinimizedReason(),
            convertAuthorAssociation(comment.getAuthorAssociation()),
            toInstant(comment.getCreatedAt()),
            toInstant(comment.getUpdatedAt()),
            GitHubUserDTO.fromActor(comment.getAuthor()),
            comment.getReplyTo() != null ? comment.getReplyTo().getId() : null
        );
    }

    /**
     * Creates a list of GitHubDiscussionCommentDTOs from a GraphQL DiscussionCommentConnection.
     * <p>
     * NOTE: This method only returns top-level comments. To include replies, use
     * {@link #fromDiscussionCommentConnectionWithReplies(GHDiscussionCommentConnection)}.
     */
    public static List<GitHubDiscussionCommentDTO> fromDiscussionCommentConnection(
        @Nullable GHDiscussionCommentConnection connection
    ) {
        if (connection == null || connection.getNodes() == null) {
            return Collections.emptyList();
        }
        return connection
            .getNodes()
            .stream()
            .map(GitHubDiscussionCommentDTO::fromDiscussionComment)
            .filter(dto -> dto != null)
            .toList();
    }

    /**
     * Creates a list of GitHubDiscussionCommentDTOs from a GraphQL DiscussionCommentConnection,
     * including all nested replies.
     * <p>
     * This is the comprehensive version that should be used for sync operations
     * to ensure no replies are lost.
     *
     * @param connection the GraphQL connection
     * @return flattened list containing both top-level comments and their replies
     */
    public static List<GitHubDiscussionCommentDTO> fromDiscussionCommentConnectionWithReplies(
        @Nullable GHDiscussionCommentConnection connection
    ) {
        if (connection == null || connection.getNodes() == null) {
            return Collections.emptyList();
        }
        List<GitHubDiscussionCommentDTO> result = new java.util.ArrayList<>();

        for (GHDiscussionComment comment : connection.getNodes()) {
            GitHubDiscussionCommentDTO dto = fromDiscussionComment(comment);
            if (dto != null) {
                result.add(dto);

                // Process replies
                if (comment.getReplies() != null && comment.getReplies().getNodes() != null) {
                    for (GHDiscussionComment reply : comment.getReplies().getNodes()) {
                        GitHubDiscussionCommentDTO replyDto = fromReply(reply, comment.getId());
                        if (replyDto != null) {
                            result.add(replyDto);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Creates a GitHubDiscussionCommentDTO from a reply comment, with the parent's node ID.
     * <p>
     * Replies in the GraphQL API don't have a replyTo field populated when nested,
     * so we need to track the parent explicitly.
     */
    @Nullable
    private static GitHubDiscussionCommentDTO fromReply(
        @Nullable GHDiscussionComment reply,
        @Nullable String parentNodeId
    ) {
        if (reply == null) {
            return null;
        }

        return new GitHubDiscussionCommentDTO(
            null,
            reply.getDatabaseId() != null ? reply.getDatabaseId().longValue() : null,
            reply.getId(),
            reply.getBody(),
            uriToString(reply.getUrl()),
            reply.getIsAnswer(),
            reply.getIsMinimized(),
            reply.getMinimizedReason(),
            convertAuthorAssociation(reply.getAuthorAssociation()),
            toInstant(reply.getCreatedAt()),
            toInstant(reply.getUpdatedAt()),
            GitHubUserDTO.fromActor(reply.getAuthor()),
            parentNodeId
        );
    }

    /**
     * Extracts page info for replies from a comment.
     * Used to track which comments need additional reply pagination.
     *
     * @param comment the GraphQL comment
     * @return the page info for replies, or null if no replies or no pagination needed
     */
    @Nullable
    public static GHPageInfo getReplyPageInfo(@Nullable GHDiscussionComment comment) {
        if (comment == null || comment.getReplies() == null) {
            return null;
        }
        return comment.getReplies().getPageInfo();
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static String convertAuthorAssociation(@Nullable GHCommentAuthorAssociation association) {
        if (association == null) {
            return null;
        }
        return association.name();
    }
}
