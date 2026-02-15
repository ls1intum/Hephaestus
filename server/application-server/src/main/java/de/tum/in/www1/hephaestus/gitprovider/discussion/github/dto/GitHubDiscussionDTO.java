package de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussion;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionStateReason;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHLockReason;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Discussions.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("number") int number,
    @JsonProperty("title") String title,
    @JsonProperty("body") String body,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("state") String state,
    @JsonProperty("state_reason") String stateReason,
    @JsonProperty("locked") boolean locked,
    @JsonProperty("active_lock_reason") String activeLockReason,
    @JsonProperty("comments") int commentsCount,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("answer_chosen_at") Instant answerChosenAt,
    @JsonProperty("user") GitHubUserDTO author,
    @JsonProperty("answer_chosen_by") GitHubUserDTO answerChosenBy,
    @JsonProperty("category") GitHubDiscussionCategoryDTO category,
    @JsonProperty("labels") List<GitHubLabelDTO> labels,
    // Embedded answer comment for GraphQL sync
    @JsonProperty("answer") GitHubDiscussionCommentDTO answerComment
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    /**
     * Check if the discussion is closed.
     */
    public boolean isClosed() {
        return "closed".equalsIgnoreCase(state);
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubDiscussionDTO from a GraphQL Discussion model.
     *
     * @param discussion the GraphQL Discussion (may be null)
     * @return GitHubDiscussionDTO or null if discussion is null
     */
    @Nullable
    public static GitHubDiscussionDTO fromDiscussion(@Nullable GHDiscussion discussion) {
        if (discussion == null) {
            return null;
        }

        return new GitHubDiscussionDTO(
            null,
            discussion.getDatabaseId() != null ? discussion.getDatabaseId().longValue() : null,
            discussion.getId(),
            discussion.getNumber(),
            discussion.getTitle(),
            discussion.getBody(),
            uriToString(discussion.getUrl()),
            discussion.getClosed() ? "closed" : "open",
            convertStateReason(discussion.getStateReason()),
            discussion.getLocked(),
            convertLockReason(discussion.getActiveLockReason()),
            discussion.getComments() != null ? discussion.getComments().getTotalCount() : 0,
            toInstant(discussion.getCreatedAt()),
            toInstant(discussion.getUpdatedAt()),
            toInstant(discussion.getClosedAt()),
            toInstant(discussion.getAnswerChosenAt()),
            GitHubUserDTO.fromActor(discussion.getAuthor()),
            GitHubUserDTO.fromActor(discussion.getAnswerChosenBy()),
            GitHubDiscussionCategoryDTO.fromDiscussionCategory(discussion.getCategory()),
            GitHubLabelDTO.fromLabelConnection(discussion.getLabels()),
            GitHubDiscussionCommentDTO.fromDiscussionComment(discussion.getAnswer())
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static String convertStateReason(@Nullable GHDiscussionStateReason stateReason) {
        if (stateReason == null) {
            return null;
        }
        return stateReason.name().toLowerCase();
    }

    @Nullable
    private static String convertLockReason(@Nullable GHLockReason lockReason) {
        if (lockReason == null) {
            return null;
        }
        return lockReason.name().toLowerCase();
    }
}
