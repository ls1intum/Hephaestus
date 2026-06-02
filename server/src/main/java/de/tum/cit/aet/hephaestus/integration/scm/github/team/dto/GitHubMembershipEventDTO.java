package de.tum.cit.aet.hephaestus.integration.scm.github.team.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubWebhookEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.user.dto.GitHubUserDTO;

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
) implements GitHubWebhookEvent {
    @Override
    public GitHubEventAction.Membership actionType() {
        return GitHubEventAction.Membership.fromString(action);
    }

    @Override
    public GitHubRepositoryRefDTO repository() {
        return null; // Membership events don't have a repository
    }
}
