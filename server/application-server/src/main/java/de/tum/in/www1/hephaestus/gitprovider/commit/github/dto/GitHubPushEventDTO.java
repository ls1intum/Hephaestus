package de.tum.in.www1.hephaestus.gitprovider.commit.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.List;

/**
 * DTO for GitHub push webhook events.
 * <p>
 * Represents the payload received when commits are pushed to a repository.
 * Contains the list of commits and metadata about the push.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPushEventDTO(
    @JsonProperty("ref") String ref,
    @JsonProperty("before") String beforeSha,
    @JsonProperty("after") String afterSha,
    @JsonProperty("created") boolean created,
    @JsonProperty("deleted") boolean deleted,
    @JsonProperty("forced") boolean forced,
    @JsonProperty("base_ref") String baseRef,
    @JsonProperty("compare") String compareUrl,
    @JsonProperty("commits") List<GitHubCommitDTO> commits,
    @JsonProperty("head_commit") GitHubCommitDTO headCommit,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("pusher") GitHubPusherDTO pusher,
    @JsonProperty("sender") GitHubUserDTO sender
) {
    /**
     * Get the branch name from the ref (e.g., "refs/heads/main" -> "main").
     */
    public String getBranchName() {
        if (ref == null) {
            return null;
        }
        if (ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        if (ref.startsWith("refs/tags/")) {
            return ref.substring("refs/tags/".length());
        }
        return ref;
    }

    /**
     * Check if this is a branch push (not a tag).
     */
    public boolean isBranchPush() {
        return ref != null && ref.startsWith("refs/heads/");
    }

    /**
     * Check if this is a tag push.
     */
    public boolean isTagPush() {
        return ref != null && ref.startsWith("refs/tags/");
    }

    /**
     * DTO for the pusher information in a push event.
     * This is different from a user - it only contains name and email.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubPusherDTO(@JsonProperty("name") String name, @JsonProperty("email") String email) {}
}
