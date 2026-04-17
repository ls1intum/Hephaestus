package de.tum.in.www1.hephaestus.gitprovider.pullrequest.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab merge request webhook events.
 * <p>
 * Maps {@code object_kind: "merge_request"} payloads from GitLab webhooks.
 * Supports actions: open, close, reopen, merge, update, approved, unapproved.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabMergeRequestEventDTO(
    @JsonProperty("object_kind") @NonNull String objectKind,
    @JsonProperty("event_type") @NonNull String eventType,
    @NonNull GitLabWebhookUser user,
    @NonNull GitLabWebhookProject project,
    @JsonProperty("object_attributes") @NonNull ObjectAttributes objectAttributes,
    @Nullable List<GitLabWebhookLabel> labels,
    @Nullable List<GitLabWebhookUser> assignees,
    @Nullable List<GitLabWebhookUser> reviewers
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(
        @NonNull Long id,
        @NonNull Integer iid,
        @NonNull String title,
        String description,
        @NonNull String state,
        @NonNull String action,
        @JsonProperty("source_branch") @NonNull String sourceBranch,
        @JsonProperty("target_branch") @NonNull String targetBranch,
        boolean draft,
        @JsonProperty("author_id") @NonNull Long authorId,
        @JsonProperty("merge_user_id") @Nullable Long mergeUserId,
        @JsonProperty("milestone_id") @Nullable Long milestoneId,
        @JsonProperty("created_at") @NonNull String createdAt,
        @JsonProperty("updated_at") @NonNull String updatedAt,
        @JsonProperty("closed_at") @Nullable String closedAt,
        @JsonProperty("merged_at") @Nullable String mergedAt,
        @NonNull String url,
        @JsonProperty("last_commit") @Nullable LastCommit lastCommit,
        @JsonProperty("merge_commit_sha") @Nullable String mergeCommitSha
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastCommit(@NonNull String id, @Nullable String message, @Nullable String title) {}

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
