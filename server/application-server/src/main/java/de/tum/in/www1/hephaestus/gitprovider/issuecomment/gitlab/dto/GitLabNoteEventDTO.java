package de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookProject;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import org.springframework.lang.Nullable;

/** DTO for GitLab note webhook events ({@code object_kind: "note"}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabNoteEventDTO(
    @JsonProperty("object_kind") String objectKind,
    @JsonProperty("event_type") String eventType,
    GitLabWebhookUser user,
    GitLabWebhookProject project,
    @JsonProperty("object_attributes") NoteAttributes objectAttributes,
    @Nullable EmbeddedIssue issue,
    @Nullable @JsonProperty("merge_request") EmbeddedMergeRequest mergeRequest
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoteAttributes(
        Long id,
        String note,
        @JsonProperty("noteable_type") String noteableType,
        boolean system,
        boolean internal,
        @Nullable Object position,
        String action,
        String url,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
    ) {}

    /** Embedded issue data — enough fields for stub creation if the parent doesn't exist yet. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddedIssue(
        Long id,
        Integer iid,
        String title,
        String description,
        String state,
        boolean confidential,
        String url,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
    ) {}

    /** Embedded merge request data — enough fields for stub creation if the parent doesn't exist yet. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddedMergeRequest(
        Long id,
        Integer iid,
        String title,
        String description,
        String state,
        boolean draft,
        @JsonProperty("source_branch") String sourceBranch,
        @JsonProperty("target_branch") String targetBranch,
        String url,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
    ) {}

    public boolean isSystemNote() {
        return objectAttributes != null && objectAttributes.system();
    }

    public boolean isInternalNote() {
        return objectAttributes != null && objectAttributes.internal();
    }

    public boolean isConfidentialIssue() {
        return issue != null && issue.confidential();
    }

    public boolean isDiffNote() {
        return objectAttributes != null && objectAttributes.position() != null;
    }

    @Nullable
    public String noteableType() {
        return objectAttributes != null ? objectAttributes.noteableType() : null;
    }

    public GitLabEventAction actionType() {
        if (objectAttributes == null || objectAttributes.action() == null) {
            return GitLabEventAction.UNKNOWN;
        }
        return GitLabEventAction.fromString(objectAttributes.action());
    }
}
