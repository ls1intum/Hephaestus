package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import org.springframework.lang.Nullable;

/**
 * Webhook event DTO for projects_v2_status_update events.
 * <p>
 * This represents the structure of the webhook payload for projects_v2_status_update events.
 * GitHub sends this when a project status update is created, edited, or deleted.
 * <p>
 * <h2>Owner Type Detection</h2>
 * <p>
 * The project's owner type can be determined from the presence of fields:
 * <ul>
 *   <li><b>Organization:</b> {@code organization} field is present</li>
 *   <li><b>Repository:</b> {@code repository} field is present but no {@code organization}</li>
 *   <li><b>User:</b> Neither {@code organization} nor {@code repository} is present</li>
 * </ul>
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#projects_v2_status_update">
 *      GitHub Projects V2 Status Update Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectStatusUpdateEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("projects_v2_status_update") StatusUpdatePayload statusUpdate,
    @JsonProperty("organization") GitHubOrganizationEventDTO.GitHubOrganizationDTO organization,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
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
        @JsonProperty("creator") GitHubUserDTO creator,
        @JsonProperty("body") String body,
        @JsonProperty("body_html") String bodyHtml,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("target_date") String targetDate,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
    ) {}

    /**
     * Detects the owner type from the webhook payload fields.
     * <p>
     * Owner type priority:
     * <ol>
     *   <li>If {@code organization} is present → ORGANIZATION</li>
     *   <li>Else if {@code repository} is present → REPOSITORY</li>
     *   <li>Else → USER (sender is the owner)</li>
     * </ol>
     *
     * @return the detected owner type, never null
     */
    public Project.OwnerType detectOwnerType() {
        if (organization != null && organization.id() != null) {
            return Project.OwnerType.ORGANIZATION;
        }
        if (repository != null && repository.id() != null) {
            return Project.OwnerType.REPOSITORY;
        }
        return Project.OwnerType.USER;
    }

    /**
     * Gets a human-readable identifier for the owner for logging purposes.
     *
     * @return the owner identifier (login, full_name, or ID)
     */
    @Nullable
    public String getOwnerIdentifier() {
        Project.OwnerType ownerType = detectOwnerType();
        return switch (ownerType) {
            case ORGANIZATION -> organization != null ? organization.login() : null;
            case REPOSITORY -> repository != null ? repository.fullName() : null;
            case USER -> sender != null ? sender.login() : null;
        };
    }
}
