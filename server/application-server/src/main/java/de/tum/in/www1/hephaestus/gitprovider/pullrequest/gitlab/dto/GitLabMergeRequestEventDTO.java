package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab merge request webhook events.
 * <p>
 * Maps {@code object_kind: "merge_request"} payloads from GitLab webhooks.
 * Supports actions: open, close, reopen, merge, update, approved, unapproved.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabMergeRequestEventDTO(
    @JsonProperty("object_kind") String objectKind,
    @JsonProperty("event_type") String eventType,
    GitLabWebhookUser user,
    GitLabWebhookProject project,
    @JsonProperty("object_attributes") ObjectAttributes objectAttributes,
    @Nullable List<GitLabWebhookLabel> labels,
    @Nullable List<GitLabWebhookUser> assignees,
    @Nullable List<GitLabWebhookUser> reviewers
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(
        Long id,
        Integer iid,
        String title,
        String description,
        String state,
        String action,
        @JsonProperty("source_branch") String sourceBranch,
        @JsonProperty("target_branch") String targetBranch,
        boolean draft,
        @JsonProperty("author_id") Long authorId,
        @JsonProperty("merge_user_id") @Nullable Long mergeUserId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("closed_at") @Nullable String closedAt,
        @JsonProperty("merged_at") @Nullable String mergedAt,
        String url
    ) {}

    public boolean isConfidential() {
        return "confidential_merge_request".equals(eventType);
    }

    public GitLabEventAction actionType() {
        if (objectAttributes == null || objectAttributes.action() == null) {
            return GitLabEventAction.UNKNOWN;
        }
        return GitLabEventAction.fromString(objectAttributes.action());
    }
}
