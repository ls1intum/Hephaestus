package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab push webhook events.
 * <p>
 * Contains the project metadata embedded in every push event, which is used
 * to upsert the repository entity when a push arrives for an unknown project.
 *
 * @see <a href="https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html#push-events">
 *      GitLab Push Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabPushEventDTO(
    @JsonProperty("object_kind") String objectKind,
    @JsonProperty("ref") String ref,
    @JsonProperty("before") String before,
    @JsonProperty("after") String after,
    @JsonProperty("checkout_sha") @Nullable String checkoutSha,
    @JsonProperty("project_id") Long projectId,
    @JsonProperty("project") ProjectInfo project,
    @JsonProperty("total_commits_count") int totalCommitsCount
) {
    /**
     * Embedded project metadata from the push event payload.
     * <p>
     * GitLab includes comprehensive project info in every push event,
     * allowing us to upsert the repository without a separate API call.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectInfo(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("description") @Nullable String description,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("namespace") @Nullable String namespace,
        @JsonProperty("path_with_namespace") String pathWithNamespace,
        @JsonProperty("default_branch") @Nullable String defaultBranch,
        @JsonProperty("visibility_level") int visibilityLevel
    ) {
        /** Visibility level 0 = private. */
        public static final int VISIBILITY_PRIVATE = 0;

        /** Visibility level 10 = internal. */
        public static final int VISIBILITY_INTERNAL = 10;

        /** Visibility level 20 = public. */
        public static final int VISIBILITY_PUBLIC = 20;
    }

    /**
     * Returns whether this push is to the default branch.
     */
    public boolean isDefaultBranch() {
        if (project == null || project.defaultBranch() == null || ref == null) {
            return false;
        }
        return ref.equals("refs/heads/" + project.defaultBranch());
    }

    /** The zero SHA used by Git to represent a null/deleted ref. */
    private static final String NULL_SHA = "0000000000000000000000000000000000000000";

    /**
     * Returns whether this is a branch deletion event.
     * <p>
     * Git signals a ref deletion by setting the {@code after} SHA to all zeros.
     */
    public boolean isBranchDeletion() {
        return NULL_SHA.equals(after);
    }
}
