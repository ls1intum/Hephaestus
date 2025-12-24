package de.tum.in.www1.hephaestus.gitprovider.team.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub membership webhook events (team membership changes).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubMembershipEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("scope") String scope,
    @JsonProperty("member") GitHubUserDTO member,
    @JsonProperty("team") GitHubTeamEventDTO.GitHubTeamDTO team,
    @JsonProperty("organization") GitHubTeamEventDTO.GitHubOrgRefDTO organization,
    @JsonProperty("sender") GitHubUserDTO sender
)
    implements GitHubWebhookEvent {
    public GitHubEventAction.Membership actionType() {
        return GitHubEventAction.Membership.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return null; // Membership events don't have a repository
    }
}
