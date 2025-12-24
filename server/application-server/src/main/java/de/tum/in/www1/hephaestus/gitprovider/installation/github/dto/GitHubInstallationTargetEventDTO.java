package de.tum.in.www1.hephaestus.gitprovider.installation.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub installation_target webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationTargetEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("installation") GitHubInstallationEventDTO.GitHubInstallationDTO installation,
    @JsonProperty("account") GitHubInstallationEventDTO.GitHubAccountDTO account,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("changes") @Nullable Changes changes,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    public GitHubEventAction.InstallationTarget actionType() {
        return GitHubEventAction.InstallationTarget.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return null;
    }

    /**
     * Changes object for rename events.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Changes(@JsonProperty("login") LoginChange login) {}

    /**
     * Login change details.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginChange(@JsonProperty("from") String from) {}
}
