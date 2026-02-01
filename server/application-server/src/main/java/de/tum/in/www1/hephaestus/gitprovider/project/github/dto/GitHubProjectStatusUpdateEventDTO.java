package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * Webhook event DTO for projects_v2_status_update events.
 * <p>
 * This represents the structure of the webhook payload for projects_v2_status_update events.
 * GitHub sends this when a project status update is created, edited, or deleted.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#projects_v2_status_update">
 *      GitHub Projects V2 Status Update Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectStatusUpdateEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("projects_v2_status_update") StatusUpdatePayload statusUpdate,
    @JsonProperty("organization") GitHubOrganizationEventDTO.GitHubOrganizationDTO organization,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("installation") InstallationRef installation
) {
    /**
     * Reference to the GitHub App installation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationRef(@JsonProperty("id") Long id, @JsonProperty("node_id") String nodeId) {}

    /**
     * Payload containing the status update details.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusUpdatePayload(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("project_node_id") String projectNodeId,
        @JsonProperty("creator_id") Long creatorId,
        @JsonProperty("body") String body,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("target_date") String targetDate,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
    ) {}
}
