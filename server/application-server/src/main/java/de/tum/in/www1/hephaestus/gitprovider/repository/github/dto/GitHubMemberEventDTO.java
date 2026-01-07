package de.tum.in.www1.hephaestus.gitprovider.repository.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub member webhook events (collaborator changes).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubMemberEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("member") GitHubUserDTO member,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("changes") Map<String, Object> changes
) implements GitHubWebhookEvent {
    public GitHubEventAction.Member actionType() {
        return GitHubEventAction.Member.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * Extracts the permission value from the changes map.
     * The permission is nested as changes.permission.to in the webhook payload.
     *
     * @return the permission string (e.g., "write", "admin"), or null if not present
     */
    public String getPermission() {
        if (changes == null) {
            return null;
        }
        Object permissionObj = changes.get("permission");
        if (permissionObj instanceof Map<?, ?> permissionMap) {
            Object toValue = permissionMap.get("to");
            return toValue != null ? toValue.toString() : null;
        }
        return null;
    }
}
