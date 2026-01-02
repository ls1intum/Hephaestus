package de.tum.in.www1.hephaestus.gitprovider.commit.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Commit;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GitActor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub commits.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDTO(
    @JsonProperty("id") String sha,
    @JsonProperty("sha") String shaAlias,
    @JsonProperty("abbreviated_sha") String abbreviatedSha,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("message") String message,
    @JsonProperty("message_headline") String messageHeadline,
    @JsonProperty("url") String htmlUrl,
    @JsonProperty("authored_date") Instant authoredAt,
    @JsonProperty("committed_date") Instant committedAt,
    @JsonProperty("author_name") String authorName,
    @JsonProperty("author_email") String authorEmail,
    @JsonProperty("committer_name") String committerName,
    @JsonProperty("committer_email") String committerEmail,
    @JsonProperty("additions") Integer additions,
    @JsonProperty("deletions") Integer deletions,
    @JsonProperty("changed_files") Integer changedFiles,
    @JsonProperty("author") GitHubUserDTO author,
    @JsonProperty("committer") GitHubUserDTO committer,
    @JsonProperty("distinct") boolean distinct,
    @JsonProperty("added") List<String> addedFiles,
    @JsonProperty("removed") List<String> removedFiles,
    @JsonProperty("modified") List<String> modifiedFiles,
    @JsonProperty("parents_count") Integer parentsCount
) {
    /**
     * Get the SHA, preferring sha over shaAlias (for webhook payloads that use 'id').
     */
    public String getSha() {
        return sha != null ? sha : shaAlias;
    }

    /**
     * Check if this is a merge commit (has more than one parent).
     */
    public boolean isMergeCommit() {
        return parentsCount != null && parentsCount > 1;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubCommitDTO from a GraphQL Commit model.
     *
     * @param commit the GraphQL Commit (may be null)
     * @return GitHubCommitDTO or null if commit is null
     */
    @Nullable
    public static GitHubCommitDTO fromCommit(@Nullable Commit commit) {
        if (commit == null) {
            return null;
        }

        GitActor author = commit.getAuthor();
        GitActor committer = commit.getCommitter();

        return new GitHubCommitDTO(
            commit.getOid(),
            null,
            commit.getAbbreviatedOid(),
            commit.getId(),
            commit.getMessage(),
            commit.getMessageHeadline(),
            uriToString(commit.getUrl()),
            toInstant(commit.getAuthoredDate()),
            toInstant(commit.getCommittedDate()),
            author != null ? author.getName() : null,
            author != null ? author.getEmail() : null,
            committer != null ? committer.getName() : null,
            committer != null ? committer.getEmail() : null,
            commit.getAdditions(),
            commit.getDeletions(),
            commit.getChangedFilesIfAvailable(),
            extractUser(author),
            extractUser(committer),
            true, // GraphQL commits are always distinct
            null, // File lists not available in GraphQL
            null,
            null,
            extractParentsCount(commit)
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static Instant toInstant(@Nullable OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }

    @Nullable
    private static String uriToString(@Nullable URI uri) {
        return uri != null ? uri.toString() : null;
    }

    @Nullable
    private static GitHubUserDTO extractUser(@Nullable GitActor actor) {
        if (actor == null || actor.getUser() == null) {
            return null;
        }
        return GitHubUserDTO.fromUser(actor.getUser());
    }

    @Nullable
    private static Integer extractParentsCount(@Nullable Commit commit) {
        if (commit == null || commit.getParents() == null) {
            return null;
        }
        return commit.getParents().getTotalCount();
    }
}
