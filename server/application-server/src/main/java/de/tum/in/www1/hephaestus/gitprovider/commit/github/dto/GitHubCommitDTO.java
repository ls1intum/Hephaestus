package de.tum.in.www1.hephaestus.gitprovider.commit.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHCommit;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHCommitConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHGitActor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Commits.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
 * <p>
 * Note: Commits use SHA (String) as the primary identifier, not a numeric database ID.
 * The author/committer may be null if the git email doesn't map to a GitHub user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDTO(
    @JsonProperty("sha") String sha,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("message") String message,
    @JsonProperty("message_headline") String messageHeadline,
    @JsonProperty("message_body") String messageBody,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("authored_at") Instant authoredAt,
    @JsonProperty("committed_at") Instant committedAt,
    @JsonProperty("additions") int additions,
    @JsonProperty("deletions") int deletions,
    @JsonProperty("changed_files") Integer changedFiles,
    @JsonProperty("author") GitHubUserDTO author,
    @JsonProperty("committer") GitHubUserDTO committer,
    // Git actor info (for cases where there's no GitHub user)
    @JsonProperty("author_name") String authorName,
    @JsonProperty("author_email") String authorEmail,
    @JsonProperty("committer_name") String committerName,
    @JsonProperty("committer_email") String committerEmail,
    // Parent count for merge detection
    @JsonProperty("parent_count") int parentCount
) {
    /**
     * Compact constructor for basic commit data.
     */
    public GitHubCommitDTO(
        String sha,
        String nodeId,
        String message,
        String messageHeadline,
        String messageBody,
        String htmlUrl,
        Instant authoredAt,
        Instant committedAt,
        int additions,
        int deletions,
        Integer changedFiles,
        GitHubUserDTO author,
        GitHubUserDTO committer
    ) {
        this(
            sha,
            nodeId,
            message,
            messageHeadline,
            messageBody,
            htmlUrl,
            authoredAt,
            committedAt,
            additions,
            deletions,
            changedFiles,
            author,
            committer,
            null,
            null,
            null,
            null,
            1 // default to 1 parent (non-merge)
        );
    }

    /**
     * Check if this is a merge commit (has more than one parent).
     */
    public boolean isMergeCommit() {
        return parentCount > 1;
    }

    /**
     * Get the short SHA (first 7 characters) for display purposes.
     */
    public String getShortSha() {
        return sha != null && sha.length() >= 7 ? sha.substring(0, 7) : sha;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubCommitDTO from a GraphQL Commit model.
     * <p>
     * Extracts the GitHub user from the GitActor if available.
     * The GitActor contains both the raw git info (name, email) and
     * optionally a linked GitHub User account.
     *
     * @param commit the GraphQL Commit (may be null)
     * @return GitHubCommitDTO or null if commit is null
     */
    @Nullable
    public static GitHubCommitDTO fromCommit(@Nullable GHCommit commit) {
        if (commit == null) {
            return null;
        }

        GHGitActor authorActor = commit.getAuthor();
        GHGitActor committerActor = commit.getCommitter();

        // Extract GitHub users if available (may be null if git email doesn't map to GitHub user)
        GitHubUserDTO authorDto = extractUserFromGitActor(authorActor);
        GitHubUserDTO committerDto = extractUserFromGitActor(committerActor);

        // Determine parent count for merge detection
        int parentCount = 1;
        GHCommitConnection parents = commit.getParents();
        if (parents != null) {
            parentCount = parents.getTotalCount();
        }

        return new GitHubCommitDTO(
            commit.getOid(),
            commit.getId(),
            commit.getMessage(),
            commit.getMessageHeadline(),
            commit.getMessageBody(),
            uriToString(commit.getUrl()),
            toInstant(commit.getAuthoredDate()),
            toInstant(commit.getCommittedDate()),
            commit.getAdditions(),
            commit.getDeletions(),
            commit.getChangedFilesIfAvailable(),
            authorDto,
            committerDto,
            // Raw git actor info
            authorActor != null ? authorActor.getName() : null,
            authorActor != null ? authorActor.getEmail() : null,
            committerActor != null ? committerActor.getName() : null,
            committerActor != null ? committerActor.getEmail() : null,
            parentCount
        );
    }

    /**
     * Extracts a GitHubUserDTO from a GitActor if a GitHub user is linked.
     * <p>
     * GitActor represents the author/committer in a git commit and may or may not
     * have an associated GitHub user account. If the git email maps to a GitHub
     * user, the User object will be populated.
     *
     * @param gitActor the GitActor (may be null)
     * @return GitHubUserDTO or null if no GitHub user is linked
     */
    @Nullable
    private static GitHubUserDTO extractUserFromGitActor(@Nullable GHGitActor gitActor) {
        if (gitActor == null || gitActor.getUser() == null) {
            return null;
        }
        GHUser user = gitActor.getUser();
        return new GitHubUserDTO(
            null,
            user.getDatabaseId() != null ? user.getDatabaseId().longValue() : null,
            user.getLogin(),
            uriToString(user.getAvatarUrl()),
            uriToString(user.getUrl()),
            user.getName(),
            user.getEmail()
        );
    }

    // ========== STATIC FACTORY METHODS FOR WEBHOOK PAYLOADS ==========

    /**
     * Creates a GitHubCommitDTO from a push webhook commit.
     * <p>
     * Push webhook payloads have a simplified commit structure compared to GraphQL.
     * The author/committer are git author info (name, email) without GitHub user linking.
     * Users will be resolved later by the processor using the username if available.
     *
     * @param pushCommit the commit from a push webhook payload
     * @param repoHtmlUrl the repository HTML URL for constructing commit URLs
     * @return GitHubCommitDTO or null if pushCommit is null
     */
    @Nullable
    public static GitHubCommitDTO fromPushWebhook(
        @Nullable GitHubPushEventDTO.PushCommit pushCommit,
        @Nullable String repoHtmlUrl
    ) {
        if (pushCommit == null || pushCommit.sha() == null) {
            return null;
        }

        // Try to create GitHubUserDTO from username if available
        GitHubUserDTO authorDto = null;
        GitHubUserDTO committerDto = null;

        if (pushCommit.author() != null && pushCommit.author().username() != null) {
            authorDto = new GitHubUserDTO(
                null,
                null,
                pushCommit.author().username(),
                null,
                null,
                pushCommit.author().name(),
                pushCommit.author().email()
            );
        }
        if (pushCommit.committer() != null && pushCommit.committer().username() != null) {
            committerDto = new GitHubUserDTO(
                null,
                null,
                pushCommit.committer().username(),
                null,
                null,
                pushCommit.committer().name(),
                pushCommit.committer().email()
            );
        }

        // Construct HTML URL from repo URL if not provided
        String htmlUrl = pushCommit.url();
        if (htmlUrl == null && repoHtmlUrl != null) {
            htmlUrl = repoHtmlUrl + "/commit/" + pushCommit.sha();
        }

        return new GitHubCommitDTO(
            pushCommit.sha(),
            null, // nodeId not available in webhook
            pushCommit.message(),
            pushCommit.getMessageHeadline(),
            pushCommit.getMessageBody(),
            htmlUrl,
            pushCommit.timestamp(), // use timestamp as authored date
            pushCommit.timestamp(), // and as committed date
            0, // additions not available in webhook
            0, // deletions not available in webhook
            pushCommit.getChangedFilesCount(),
            authorDto,
            committerDto,
            pushCommit.author() != null ? pushCommit.author().name() : null,
            pushCommit.author() != null ? pushCommit.author().email() : null,
            pushCommit.committer() != null ? pushCommit.committer().name() : null,
            pushCommit.committer() != null ? pushCommit.committer().email() : null,
            1 // parent count not easily available in webhook, default to non-merge
        );
    }

    /**
     * Creates a list of GitHubCommitDTOs from a push webhook event.
     *
     * @param pushEvent the push webhook event
     * @return list of GitHubCommitDTOs (never null, may be empty)
     */
    public static List<GitHubCommitDTO> fromPushWebhook(@Nullable GitHubPushEventDTO pushEvent) {
        if (pushEvent == null || pushEvent.commits() == null) {
            return List.of();
        }

        String repoHtmlUrl = pushEvent.repository() != null ? pushEvent.repository().htmlUrl() : null;
        List<GitHubCommitDTO> result = new ArrayList<>(pushEvent.commits().size());

        for (GitHubPushEventDTO.PushCommit commit : pushEvent.commits()) {
            GitHubCommitDTO dto = fromPushWebhook(commit, repoHtmlUrl);
            if (dto != null) {
                result.add(dto);
            }
        }

        return result;
    }
}
