package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;

/**
 * DTO for GitHub Projects V2 item webhook events.
 * <p>
 * This represents the structure of the webhook payload for projects_v2_item events.
 * GitHub sends this when an item is created, edited, deleted, archived, restored, etc.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#projects_v2_item">GitHub Projects V2 Item Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectItemEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("projects_v2_item") GitHubProjectItemDTO item,
    @JsonProperty("changes") Changes changes,
    @JsonProperty("organization") GitHubOrganizationEventDTO.GitHubOrganizationDTO organization,
    @JsonProperty("sender") GitHubUserDTO sender
) {
    /**
     * Changes made to the item. Only present for certain actions.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Changes(
        @JsonProperty("field_value") FieldValueChange fieldValue,
        @JsonProperty("archived_at") ArchivedAtChange archivedAt
    ) {}

    /**
     * Change to a field value.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldValueChange(
        @JsonProperty("field_node_id") String fieldNodeId,
        @JsonProperty("field_type") String fieldType
    ) {}

    /**
     * Change to archived status.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArchivedAtChange(@JsonProperty("from") String from, @JsonProperty("to") String to) {}
}
