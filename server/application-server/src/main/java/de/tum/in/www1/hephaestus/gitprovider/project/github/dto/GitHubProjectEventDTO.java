package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub Projects V2 webhook events.
 * <p>
 * This represents the structure of the webhook payload for projects_v2 events.
 * GitHub sends this when a project is created, edited, closed, reopened, or deleted.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#projects_v2">GitHub Projects V2 Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("projects_v2") GitHubProjectDTO project,
    @JsonProperty("organization") GitHubOrganizationEventDTO.GitHubOrganizationDTO organization,
    @JsonProperty("sender") GitHubUserDTO sender
) {}
