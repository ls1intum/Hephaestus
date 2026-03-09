package de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub discussion webhook event payloads.
 * <p>
 * Handles the following actions:
 * created, edited, deleted, pinned, unpinned, locked, unlocked,
 * transferred, category_changed, answered, unanswered, labeled, unlabeled,
 * closed, reopened
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("discussion") GitHubDiscussionDTO discussion,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("organization") GitHubOrgRefDTO organization,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("label") GitHubLabelDTO label,
    @JsonProperty("changes") Map<String, Object> changes
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Discussion actionType() {
        return GitHubEventAction.Discussion.fromString(action);
    }

    /**
     * DTO for organization reference in discussion events.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOrgRefDTO(@JsonProperty("id") Long id, @JsonProperty("login") String login) {}
}
