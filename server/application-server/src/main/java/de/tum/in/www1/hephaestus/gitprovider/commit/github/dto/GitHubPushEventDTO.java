package de.tum.in.www1.hephaestus.gitprovider.commit.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventAction;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.List;

/**
 * DTO for GitHub push webhook event payloads.
 * <p>
 * A push event is triggered when commits are pushed to a repository branch.
 * This event contains the list of commits pushed, the ref (branch) being updated,
 * and before/after commit SHAs.
 * <p>
 * Note: Push events don't have an "action" field like other webhook events.
 * We default to "pushed" for consistency with the GitHubEventAction pattern.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">
 *      GitHub Push Event Documentation</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPushEventDTO(
    /** The full git ref that was pushed (e.g., "refs/heads/main"). */
    @JsonProperty("ref") String ref,

    /** SHA of the most recent commit on ref before the push. */
    @JsonProperty("before") String before,

    /** SHA of the most recent commit on ref after the push. */
    @JsonProperty("after") String after,

    /** Whether this push created the ref (e.g., new branch). */
    @JsonProperty("created") boolean created,

    /** Whether this push deleted the ref. */
    @JsonProperty("deleted") boolean deleted,

    /** Whether this was a force push. */
    @JsonProperty("forced") boolean forced,

    /** The base ref or target branch of the push (e.g., "refs/heads/main"). */
    @JsonProperty("base_ref") String baseRef,

    /** URL comparing the before and after commits. */
    @JsonProperty("compare") String compareUrl,

    /** List of commits pushed. May be empty for tags or force pushes. */
    @JsonProperty("commits") List<PushCommit> commits,

    /** The head commit (most recent). May be null if commits is empty. */
    @JsonProperty("head_commit") PushCommit headCommit,

    /** The repository where the commits were pushed. */
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,

    /** The user who performed the push. */
    @JsonProperty("pusher") Pusher pusher,

    /** The GitHub user who triggered the event. */
    @JsonProperty("sender") GitHubUserDTO sender
) implements GitHubWebhookEvent {
    /**
     * Push events don't have an action field, so we return null.
     */
    @Override
    public String action() {
        return null;
    }

    /**
     * Returns PUSHED as the default action type for push events.
     */
    @Override
    public GitHubEventAction.Push actionType() {
        return GitHubEventAction.Push.PUSHED;
    }

    /**
     * Gets the branch name from the ref (strips "refs/heads/").
     */
    public String getBranchName() {
        if (ref != null && ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        return ref;
    }

    /**
     * Check if this push is to the default branch.
     * Requires comparing with repository.defaultBranch().
     */
    public boolean isDefaultBranch(String defaultBranch) {
        return defaultBranch != null && defaultBranch.equals(getBranchName());
    }

    /**
     * Commit information in a push event payload.
     * Note: This is a simplified format compared to the full Commit API response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PushCommit(
        @JsonProperty("id") String sha,
        @JsonProperty("tree_id") String treeId,
        @JsonProperty("distinct") boolean distinct,
        @JsonProperty("message") String message,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("url") String url,
        @JsonProperty("author") GitAuthor author,
        @JsonProperty("committer") GitAuthor committer,
        @JsonProperty("added") List<String> added,
        @JsonProperty("removed") List<String> removed,
        @JsonProperty("modified") List<String> modified
    ) {
        /**
         * Get the total number of files changed in this commit.
         */
        public int getChangedFilesCount() {
            int count = 0;
            if (added != null) count += added.size();
            if (removed != null) count += removed.size();
            if (modified != null) count += modified.size();
            return count;
        }

        /**
         * Get the first line of the commit message (headline).
         */
        public String getMessageHeadline() {
            if (message == null || message.isBlank()) {
                return "";
            }
            int newlineIndex = message.indexOf('\n');
            if (newlineIndex > 0) {
                return message.substring(0, newlineIndex).trim();
            }
            return message.trim();
        }

        /**
         * Get the body of the commit message (everything after the first line).
         */
        public String getMessageBody() {
            if (message == null || message.isBlank()) {
                return null;
            }
            int newlineIndex = message.indexOf('\n');
            if (newlineIndex > 0 && newlineIndex < message.length() - 1) {
                return message.substring(newlineIndex + 1).trim();
            }
            return null;
        }
    }

    /**
     * Git author information (name and email) in push webhook payloads.
     * Note: This may not have a corresponding GitHub user account.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitAuthor(
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("username") String username
    ) {}

    /**
     * Pusher information (the user who performed the push).
     * May be a deploy key for automated pushes.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pusher(@JsonProperty("name") String name, @JsonProperty("email") String email) {}
}
