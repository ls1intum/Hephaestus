package de.tum.cit.aet.hephaestus.integration.scm.github.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * DTO for GitHub installation_repositories webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationRepositoriesEventDTO(
        @JsonProperty("action") String action,
        @JsonProperty("installation") GitHubInstallationEventDTO.@Nullable GitHubInstallationDTO installation,
        @JsonProperty("repositories_added") @Nullable List<GitHubRepositoryRefDTO> repositoriesAdded,
        @JsonProperty("repositories_removed") @Nullable List<GitHubRepositoryRefDTO> repositoriesRemoved,
        @JsonProperty("sender") @Nullable GitHubUserDTO sender)
        implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.InstallationRepositories actionType() {
        return GitHubEventAction.InstallationRepositories.fromString(action);
    }

    @Override
    public @Nullable GitHubRepositoryRefDTO repository() {
        return null; // This event covers multiple repositories
    }
}
