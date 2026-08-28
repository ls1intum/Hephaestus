package de.tum.cit.aet.hephaestus.integration.scm.github.discussioncomment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.discussion.dto.GitHubDiscussionDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;

/**
 * DTO for GitHub discussion_comment webhook event payloads.
 * <p>
 * Handles the following actions: created, edited, deleted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionCommentEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("comment") GitHubDiscussionCommentDTO comment,
    @JsonProperty("discussion") GitHubDiscussionDTO discussion,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.DiscussionComment actionType() {
        return GitHubEventAction.DiscussionComment.fromString(action);
    }
}
