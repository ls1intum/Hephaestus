package de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab milestone webhook events.
 * <p>
 * Maps the {@code object_kind: "milestone"} webhook payload.
 * <p>
 * <b>IMPORTANT:</b> Unlike issue/MR webhooks where {@code action} is inside
 * {@code object_attributes}, milestone webhooks place {@code action} at the
 * top level of the payload.
 *
 * @param objectKind       always {@code "milestone"}
 * @param eventType        always {@code "milestone"}
 * @param action           webhook action at top level ({@code "create"}, {@code "update"}, {@code "close"}, {@code "reopen"})
 * @param project          the project data
 * @param objectAttributes the milestone data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabMilestoneEventDTO(
    @JsonProperty("object_kind") String objectKind,
    @JsonProperty("event_type") String eventType,
    String action,
    GitLabWebhookProject project,
    @JsonProperty("object_attributes") ObjectAttributes objectAttributes
) {
    /**
     * Returns the parsed action type.
     */
    public GitLabEventAction actionType() {
        return GitLabEventAction.fromString(action);
    }

    /**
     * Milestone attributes from the webhook payload.
     *
     * @param id        the GitLab milestone database ID
     * @param iid       the project-scoped milestone number
     * @param title     the milestone title
     * @param description optional description
     * @param state     raw state ({@code "active"} or {@code "closed"})
     * @param createdAt creation timestamp in webhook format
     * @param updatedAt update timestamp in webhook format
     * @param dueDate   date-only string (e.g., {@code "2026-06-01"})
     * @param projectId the GitLab project database ID
     * @param groupId   the GitLab group database ID (null for project milestones)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(
        long id,
        int iid,
        String title,
        @Nullable String description,
        @Nullable String state,
        @JsonProperty("created_at") @Nullable String createdAt,
        @JsonProperty("updated_at") @Nullable String updatedAt,
        @JsonProperty("due_date") @Nullable String dueDate,
        @JsonProperty("project_id") @Nullable Long projectId,
        @JsonProperty("group_id") @Nullable Long groupId
    ) {}
}
