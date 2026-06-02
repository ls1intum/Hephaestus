package de.tum.cit.aet.hephaestus.integration.scm.github.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.util.List;

/**
 * DTO for GitHub installation webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("installation") GitHubInstallationDTO installation,
    @JsonProperty("repositories") List<GitHubRepositoryRefDTO> repositories,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Installation actionType() {
        return GitHubEventAction.Installation.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return null; // Installation events don't have a single repository
    }

    /**
     * DTO for the installation within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubInstallationDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("app_id") Long appId,
        @JsonProperty("target_type") String targetType,
        @JsonProperty("repository_selection") String repositorySelection,
        @JsonProperty("permissions") Object permissions,
        @JsonProperty("events") List<String> events,
        @JsonProperty("account") GitHubAccountDTO account
    ) {}

    /**
     * DTO for the account (user or org) the app is installed on.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubAccountDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("login") String login,
        @JsonProperty("type") String type,
        @JsonProperty("avatar_url") String avatarUrl
    ) {}
}
