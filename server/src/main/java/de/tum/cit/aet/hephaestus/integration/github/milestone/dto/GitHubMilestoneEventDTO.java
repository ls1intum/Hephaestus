package de.tum.cit.aet.hephaestus.integration.github.milestone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.github.user.dto.GitHubUserDTO;

/**
 * DTO for GitHub milestone webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubMilestoneEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("milestone") GitHubMilestoneDTO milestone,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Milestone actionType() {
        return GitHubEventAction.Milestone.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }
}
