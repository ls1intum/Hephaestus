package de.tum.cit.aet.hephaestus.integration.scm.github.team.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

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
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Team actionType() {
        return GitHubEventAction.Team.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return repository;
    }

    /**
     * DTO for the team within the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubTeamDTO(
        @JsonProperty("id") @Nullable Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("name") String name,
        @JsonProperty("slug") String slug,
        @JsonProperty("description") String description,
        @JsonProperty("privacy") @Nullable String privacy,
        @JsonProperty("permission") @Nullable String permission,
        @JsonProperty("html_url") @Nullable String htmlUrl,
        @JsonProperty("created_at") @Nullable Instant createdAt,
        @JsonProperty("updated_at") @Nullable Instant updatedAt
    ) {}

    /**
     * DTO for organization reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOrgRefDTO(@JsonProperty("id") Long id, @JsonProperty("login") String login) {}
}
