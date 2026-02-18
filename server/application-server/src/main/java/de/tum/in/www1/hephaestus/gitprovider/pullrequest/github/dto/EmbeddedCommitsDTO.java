package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHCommit;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHGitActor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestCommit;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestCommitConnection;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * DTO for embedded commits fetched inline with pull requests.
 * <p>
 * Contains the commits fetched in the initial PR query (first 100) plus the total count
 * to determine if the PR has more commits than were fetched.
 */
public record EmbeddedCommitsDTO(List<CommitData> commits, int totalCount) {
    /**
     * Creates an EmbeddedCommitsDTO from a GraphQL GHPullRequestCommitConnection.
     *
     * @param connection the GraphQL connection (may be null)
     * @return EmbeddedCommitsDTO or empty DTO if connection is null
     */
    public static EmbeddedCommitsDTO fromConnection(@Nullable GHPullRequestCommitConnection connection) {
        if (connection == null) {
            return empty();
        }

        List<CommitData> commits =
            connection.getNodes() != null
                ? connection
                      .getNodes()
                      .stream()
                      .map(CommitData::fromPullRequestCommit)
                      .filter(Objects::nonNull)
                      .toList()
                : Collections.emptyList();

        return new EmbeddedCommitsDTO(commits, connection.getTotalCount());
    }

    /**
     * Returns an empty EmbeddedCommitsDTO.
     */
    public static EmbeddedCommitsDTO empty() {
        return new EmbeddedCommitsDTO(Collections.emptyList(), 0);
    }

    /**
     * Flat representation of a commit extracted from a PullRequestCommit node.
     * Contains all the data needed to upsert into the git_commit table.
     */
    public record CommitData(
        String sha,
        String message,
        @Nullable String messageBody,
        @Nullable String htmlUrl,
        @Nullable Instant authoredAt,
        @Nullable Instant committedAt,
        int additions,
        int deletions,
        @Nullable Integer changedFiles,
        @Nullable String authorLogin,
        @Nullable String authorEmail,
        @Nullable String committerLogin,
        @Nullable String committerEmail
    ) {
        /**
         * Creates a CommitData from a GraphQL GHPullRequestCommit node.
         *
         * @param prCommit the PullRequestCommit node (may be null)
         * @return CommitData or null if the node or its commit is null/has no OID
         */
        @Nullable
        static CommitData fromPullRequestCommit(@Nullable GHPullRequestCommit prCommit) {
            if (prCommit == null || prCommit.getCommit() == null) {
                return null;
            }

            GHCommit commit = prCommit.getCommit();
            if (commit.getOid() == null || commit.getOid().isBlank()) {
                return null;
            }

            return new CommitData(
                commit.getOid(),
                commit.getMessageHeadline(),
                commit.getMessageBody(),
                uriToString(commit.getUrl()),
                toInstant(commit.getAuthoredDate()),
                toInstant(commit.getCommittedDate()),
                commit.getAdditions(),
                commit.getDeletions(),
                commit.getChangedFilesIfAvailable(),
                extractLogin(commit.getAuthor()),
                extractEmail(commit.getAuthor()),
                extractLogin(commit.getCommitter()),
                extractEmail(commit.getCommitter())
            );
        }

        @Nullable
        private static String extractLogin(@Nullable GHGitActor actor) {
            if (actor == null || actor.getUser() == null) {
                return null;
            }
            return actor.getUser().getLogin();
        }

        @Nullable
        private static String extractEmail(@Nullable GHGitActor actor) {
            if (actor == null) {
                return null;
            }
            return actor.getEmail();
        }
    }
}
