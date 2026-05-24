package de.tum.cit.aet.hephaestus.integration.github.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.github.user.dto.GitHubUserDTO;
import java.util.List;

/**
 * DTO for GitHub installation_repositories webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationRepositoriesEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("installation") GitHubInstallationEventDTO.GitHubInstallationDTO installation,
    @JsonProperty("repositories_added") List<GitHubRepositoryRefDTO> repositoriesAdded,
    @JsonProperty("repositories_removed") List<GitHubRepositoryRefDTO> repositoriesRemoved,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.InstallationRepositories actionType() {
        return GitHubEventAction.InstallationRepositories.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return null; // This event covers multiple repositories
    }
}
