package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab project webhook events (project lifecycle changes).
 * <p>
 * Project events use {@code event_name} (not {@code object_kind}) as the discriminator.
 * The webhook-ingest normalizes all project event names to "project" for NATS routing.
 * <p>
 * These events are only available on group-level webhooks.
 * <p>
 * Supported event_name values:
 * <ul>
 *   <li>{@code project_create} - Project created in a group</li>
 *   <li>{@code project_destroy} - Project deleted from a group</li>
 *   <li>{@code project_rename} - Project renamed</li>
 *   <li>{@code project_transfer} - Project transferred to a different group</li>
 *   <li>{@code project_update} - Project settings updated</li>
 * </ul>
 *
 * @see <a href="https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html#project-events">
 *      GitLab Project Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabProjectEventDTO(
    @JsonProperty("event_name") String eventName,
    @JsonProperty("name") String name,
    @JsonProperty("path") @Nullable String path,
    @JsonProperty("path_with_namespace") @Nullable String pathWithNamespace,
    @JsonProperty("old_path_with_namespace") @Nullable String oldPathWithNamespace,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("project_visibility") @Nullable String projectVisibility,
    @JsonProperty("created_at") @Nullable String createdAt,
    @JsonProperty("updated_at") @Nullable String updatedAt
) {
    public static final String EVENT_PROJECT_CREATE = "project_create";
    public static final String EVENT_PROJECT_DESTROY = "project_destroy";
    public static final String EVENT_PROJECT_RENAME = "project_rename";
    public static final String EVENT_PROJECT_TRANSFER = "project_transfer";
    public static final String EVENT_PROJECT_UPDATE = "project_update";

    public boolean isCreation() {
        return EVENT_PROJECT_CREATE.equals(eventName);
    }

    public boolean isDeletion() {
        return EVENT_PROJECT_DESTROY.equals(eventName);
    }

    public boolean isRename() {
        return EVENT_PROJECT_RENAME.equals(eventName);
    }

    public boolean isTransfer() {
        return EVENT_PROJECT_TRANSFER.equals(eventName);
    }
}
