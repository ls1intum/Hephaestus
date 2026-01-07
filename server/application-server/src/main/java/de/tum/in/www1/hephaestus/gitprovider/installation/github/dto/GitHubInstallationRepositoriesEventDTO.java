package de.tum.in.www1.hephaestus.gitprovider.installation.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
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
    public GitHubRepositoryRefDTO repository() {
        return null; // This event covers multiple repositories
    }
}
