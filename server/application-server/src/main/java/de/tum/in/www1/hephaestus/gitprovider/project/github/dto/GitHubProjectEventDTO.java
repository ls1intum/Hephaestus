package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub Projects V2 webhook events.
 * <p>
 * This represents the structure of the webhook payload for projects_v2 events.
 * GitHub sends this when a project is created, edited, closed, reopened, or deleted.
 * <p>
 * <h2>Owner Type Detection</h2>
 * <p>
 * GitHub Projects V2 can be owned by three types of entities:
 * <ul>
 *   <li><b>Organization:</b> {@code organization} field is present</li>
 *   <li><b>Repository:</b> {@code repository} field is present but no {@code organization}</li>
 *   <li><b>User:</b> Neither {@code organization} nor {@code repository} is present,
 *       only {@code sender} (the project owner)</li>
 * </ul>
 * <p>
 * Use {@link #detectOwnerType()} to determine the owner type from the webhook payload.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#projects_v2">GitHub Projects V2 Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("projects_v2") GitHubProjectDTO project,
    @JsonProperty("organization") GitHubOrganizationEventDTO.GitHubOrganizationDTO organization,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender
) {
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
     * Gets the owner ID based on the detected owner type.
     * <p>
     * Returns the ID of:
     * <ul>
     *   <li>Organization ID for ORGANIZATION projects</li>
     *   <li>Repository ID for REPOSITORY projects</li>
     *   <li>Sender (user) ID for USER projects</li>
     * </ul>
     *
     * @return the owner ID, or null if not determinable
     */
    @Nullable
    public Long getOwnerId() {
        Project.OwnerType ownerType = detectOwnerType();
        return switch (ownerType) {
            case ORGANIZATION -> organization != null ? organization.id() : null;
            case REPOSITORY -> repository != null ? repository.id() : null;
            case USER -> sender != null ? sender.getDatabaseId() : null;
        };
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
