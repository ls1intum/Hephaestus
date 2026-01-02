package de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.DiscussionStateReason;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.LockReason;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub discussions.
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
    @JsonProperty("state") String state,
    @JsonProperty("state_reason") String stateReason,
    @JsonProperty("html_url") String htmlUrl,
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
    @JsonProperty("answer") GitHubDiscussionCommentDTO answer,
    @JsonProperty("labels") List<GitHubLabelDTO> labels,
    @JsonProperty("comments_list") List<GitHubDiscussionCommentDTO> comments,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubDiscussionDTO from a GraphQL Discussion model.
     *
     * @param discussion the GraphQL Discussion (may be null)
     * @return GitHubDiscussionDTO or null if discussion is null
     */
    @Nullable
    public static GitHubDiscussionDTO fromDiscussion(@Nullable Discussion discussion) {
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
            convertClosed(discussion.getClosed()),
            convertStateReason(discussion.getStateReason()),
            uriToString(discussion.getUrl()),
            Boolean.TRUE.equals(discussion.getLocked()),
            convertLockReason(discussion.getActiveLockReason()),
            extractCommentsCount(discussion),
            toInstant(discussion.getCreatedAt()),
            toInstant(discussion.getUpdatedAt()),
            toInstant(discussion.getClosedAt()),
            toInstant(discussion.getAnswerChosenAt()),
            GitHubUserDTO.fromActor(discussion.getAuthor()),
            GitHubUserDTO.fromActor(discussion.getAnswerChosenBy()),
            GitHubDiscussionCategoryDTO.fromDiscussionCategory(discussion.getCategory()),
            GitHubDiscussionCommentDTO.fromDiscussionComment(discussion.getAnswer()),
            GitHubLabelDTO.fromLabelConnection(discussion.getLabels()),
            extractComments(discussion),
            null
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static Instant toInstant(@Nullable OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }

    @Nullable
    private static String uriToString(@Nullable URI uri) {
        return uri != null ? uri.toString() : null;
    }

    private static String convertClosed(@Nullable Boolean closed) {
        return Boolean.TRUE.equals(closed) ? "closed" : "open";
    }

    @Nullable
    private static String convertStateReason(@Nullable DiscussionStateReason stateReason) {
        if (stateReason == null) {
            return null;
        }
        return stateReason.name().toLowerCase();
    }

    @Nullable
    private static String convertLockReason(@Nullable LockReason lockReason) {
        if (lockReason == null) {
            return null;
        }
        return lockReason.name().toLowerCase();
    }

    private static int extractCommentsCount(@Nullable Discussion discussion) {
        if (discussion == null || discussion.getComments() == null) {
            return 0;
        }
        Integer totalCount = discussion.getComments().getTotalCount();
        return totalCount != null ? totalCount : 0;
    }

    private static List<GitHubDiscussionCommentDTO> extractComments(@Nullable Discussion discussion) {
        if (discussion == null || discussion.getComments() == null || discussion.getComments().getNodes() == null) {
            return Collections.emptyList();
        }
        return discussion
            .getComments()
            .getNodes()
            .stream()
            .map(GitHubDiscussionCommentDTO::fromDiscussionComment)
            .filter(dto -> dto != null)
            .toList();
    }
}
