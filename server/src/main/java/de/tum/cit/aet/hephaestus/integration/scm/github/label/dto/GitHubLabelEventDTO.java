package de.tum.cit.aet.hephaestus.integration.scm.github.label.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;

/**
 * DTO for GitHub label webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubLabelEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("label") GitHubLabelDTO label,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Label actionType() {
        return GitHubEventAction.Label.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }
}
