package de.tum.cit.aet.hephaestus.integration.scm.github.organization.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * DTO for GitHub organization webhook events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubOrganizationEventDTO(
        @JsonProperty("action") String action,
        @JsonProperty("organization") GitHubOrganizationDTO organization,
        @JsonProperty("membership") GitHubMembershipDTO membership,
        @JsonProperty("sender") GitHubUserDTO sender)
        implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Organization actionType() {
        return GitHubEventAction.Organization.fromString(action);
    }

    @Override
    public @Nullable GitHubRepositoryRefDTO repository() {
        return null; // Organization events don't have a repository
    }

    /**
     * DTO for the organization within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOrganizationDTO(
            @JsonProperty("id") @Nullable Long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("login") String login,
            @JsonProperty("description") String description,
            @JsonProperty("avatar_url") @Nullable String avatarUrl,
            @JsonProperty("html_url") @Nullable String htmlUrl,
            @JsonProperty("created_at") @Nullable Instant createdAt,
            @JsonProperty("updated_at") @Nullable Instant updatedAt) {}

    /**
     * DTO for membership info (for member_added/removed events).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubMembershipDTO(
            @JsonProperty("user") GitHubUserDTO user,
            @JsonProperty("role") String role,
            @JsonProperty("state") String state) {}
}
