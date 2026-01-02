package de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub discussion webhook events.
 * <p>
 * Represents the payload received for discussion-related webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("discussion") GitHubDiscussionDTO discussion,
    @JsonProperty("comment") GitHubDiscussionCommentDTO comment,
    @JsonProperty("answer") GitHubDiscussionCommentDTO answer,
    @JsonProperty("old_answer") GitHubDiscussionCommentDTO oldAnswer,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) {
    /**
     * Supported discussion event actions.
     */
    public enum Action {
        CREATED,
        EDITED,
        DELETED,
        TRANSFERRED,
        PINNED,
        UNPINNED,
        LOCKED,
        UNLOCKED,
        CATEGORY_CHANGED,
        ANSWERED,
        UNANSWERED,
        LABELED,
        UNLABELED,
        CLOSED,
        REOPENED,
        UNKNOWN;

        public static Action fromString(String action) {
            if (action == null) {
                return UNKNOWN;
            }
            return switch (action.toLowerCase()) {
                case "created" -> CREATED;
                case "edited" -> EDITED;
                case "deleted" -> DELETED;
                case "transferred" -> TRANSFERRED;
                case "pinned" -> PINNED;
                case "unpinned" -> UNPINNED;
                case "locked" -> LOCKED;
                case "unlocked" -> UNLOCKED;
                case "category_changed" -> CATEGORY_CHANGED;
                case "answered" -> ANSWERED;
                case "unanswered" -> UNANSWERED;
                case "labeled" -> LABELED;
                case "unlabeled" -> UNLABELED;
                case "closed" -> CLOSED;
                case "reopened" -> REOPENED;
                default -> UNKNOWN;
            };
        }
    }

    /**
     * Get the action as an enum value.
     */
    public Action getActionEnum() {
        return Action.fromString(action);
    }
}
