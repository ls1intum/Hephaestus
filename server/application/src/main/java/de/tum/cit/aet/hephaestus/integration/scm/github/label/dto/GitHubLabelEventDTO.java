package de.tum.cit.aet.hephaestus.integration.scm.github.label.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import org.jspecify.annotations.Nullable;

/**
 * DTO for GitHub label webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubLabelEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("label") @Nullable GitHubLabelDTO label,
    @JsonProperty("repository") @Nullable GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") @Nullable GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Label actionType() {
        return GitHubEventAction.Label.fromString(action);
    }

    @Override
    public @Nullable GitHubRepositoryRefDTO repository() {
        return repository;
    }
}
