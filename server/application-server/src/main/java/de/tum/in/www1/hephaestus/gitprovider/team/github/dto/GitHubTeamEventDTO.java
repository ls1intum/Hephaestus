package de.tum.in.www1.hephaestus.gitprovider.team.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub team webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubTeamEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("team") GitHubTeamDTO team,
    @JsonProperty("organization") GitHubOrgRefDTO organization,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the team within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubTeamDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("name") String name,
        @JsonProperty("slug") String slug,
        @JsonProperty("description") String description,
        @JsonProperty("privacy") String privacy,
        @JsonProperty("permission") String permission,
        @JsonProperty("html_url") String htmlUrl
    ) {}

    /**
     * DTO for organization reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOrgRefDTO(@JsonProperty("id") Long id, @JsonProperty("login") String login) {}
}
