package de.tum.in.www1.hephaestus.gitprovider.organization.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub organization webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubOrganizationEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("organization") GitHubOrganizationDTO organization,
    @JsonProperty("membership") GitHubMembershipDTO membership,
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Organization actionType() {
        return GitHubEventAction.Organization.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return null; // Organization events don't have a repository
    }

    /**
     * DTO for the organization within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOrganizationDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("login") String login,
        @JsonProperty("description") String description,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("html_url") String htmlUrl
    ) {}

    /**
     * DTO for membership info (for member_added/removed events).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubMembershipDTO(
        @JsonProperty("user") GitHubUserDTO user,
        @JsonProperty("role") String role,
        @JsonProperty("state") String state
    ) {}
}
