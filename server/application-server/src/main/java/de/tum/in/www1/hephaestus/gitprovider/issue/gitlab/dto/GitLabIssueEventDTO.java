package de.tum.in.www1.hephaestus.gitprovider.issue.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab issue webhook events.
 * <p>
 * Maps both {@code event_type: "issue"} and {@code event_type: "confidential_issue"} payloads.
 * Both arrive on the same NATS subject ({@code object_kind: "issue"}).
 *
 * @param objectKind       always "issue"
 * @param eventType        "issue" or "confidential_issue"
 * @param user             the user who triggered the event
 * @param project          the project context
 * @param objectAttributes the issue details
 * @param labels           current labels on the issue
 * @param assignees        current assignees
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabIssueEventDTO(
    @JsonProperty("object_kind") String objectKind,
    @JsonProperty("event_type") String eventType,
    GitLabWebhookUser user,
    GitLabWebhookProject project,
    @JsonProperty("object_attributes") ObjectAttributes objectAttributes,
    @Nullable List<GitLabWebhookLabel> labels,
    @Nullable List<GitLabWebhookUser> assignees
) {
    /**
     * The issue details within the webhook payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(
        Long id,
        Integer iid,
        String title,
        String description,
        String state,
        String action,
        boolean confidential,
        @JsonProperty("author_id") Long authorId,
        @JsonProperty("assignee_id") @Nullable Long assigneeId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("closed_at") @Nullable String closedAt,
        String url
    ) {}

    /**
     * Returns true if this is a confidential issue event.
     */
    public boolean isConfidential() {
        return objectAttributes != null && objectAttributes.confidential();
    }

    /**
     * Parses the action string to a GitLabEventAction enum.
     */
    public GitLabEventAction actionType() {
        if (objectAttributes == null || objectAttributes.action() == null) {
            return GitLabEventAction.UNKNOWN;
        }
        return GitLabEventAction.fromString(objectAttributes.action());
    }
}
