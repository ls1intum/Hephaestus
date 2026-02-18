package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.EmbeddedCommitsDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.EmbeddedCommitsDTO.CommitData;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for processing commits embedded inline with pull request sync responses.
 * <p>
 * Extracts commit data from the GraphQL PR query response and upserts into the
 * {@code git_commit} table, resolving author/committer identities via
 * {@link CommitAuthorResolver}. This avoids separate API calls to fetch commits.
 */
@Service
@RequiredArgsConstructor
public class GitHubInlineCommitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubInlineCommitSyncService.class);

    private final CommitRepository commitRepository;
    private final CommitAuthorResolver commitAuthorResolver;

    /**
     * Processes embedded commits from a PR sync response.
     * <p>
     * For each commit, resolves author/committer by login (preferred, since the GraphQL
     * response includes the GitHub user) with email as fallback, then upserts into the
     * {@code git_commit} table.
     *
     * @param embeddedCommits the embedded commits DTO extracted from the PR query
     * @param repositoryId    the database ID of the repository these commits belong to
     * @return the number of commits successfully upserted
     */
    @Transactional
    public int processEmbeddedCommits(EmbeddedCommitsDTO embeddedCommits, Long repositoryId) {
        if (embeddedCommits == null || embeddedCommits.commits().isEmpty()) {
            return 0;
        }

        int upserted = 0;
        Instant syncTime = Instant.now();

        for (CommitData commit : embeddedCommits.commits()) {
            try {
                Long authorId = resolveActorId(commit.authorLogin(), commit.authorEmail());
                Long committerId = resolveActorId(commit.committerLogin(), commit.committerEmail());

                commitRepository.upsertCommit(
                    commit.sha(),
                    commit.message(),
                    commit.messageBody(),
                    commit.htmlUrl(),
                    commit.authoredAt(),
                    commit.committedAt(),
                    commit.additions(),
                    commit.deletions(),
                    commit.changedFiles(),
                    syncTime,
                    repositoryId,
                    authorId,
                    committerId
                );
                upserted++;
            } catch (Exception e) {
                log.warn("Failed to upsert commit sha={}: {}", commit.sha(), e.getMessage());
            }
        }

        if (embeddedCommits.totalCount() > embeddedCommits.commits().size()) {
            log.debug(
                "PR has {} commits but only {} were fetched inline (repositoryId={})",
                embeddedCommits.totalCount(),
                embeddedCommits.commits().size(),
                repositoryId
            );
        }

        return upserted;
    }

    /**
     * Resolves a user's database ID using login (preferred) with email as fallback.
     * <p>
     * The GraphQL response provides both the GitHub user login (via {@code author.user.login})
     * and the git email (via {@code author.email}). Login is preferred since it's a direct
     * match, while email resolution requires additional heuristics (e.g., noreply parsing).
     */
    private Long resolveActorId(String login, String email) {
        Long id = commitAuthorResolver.resolveByLogin(login);
        if (id != null) {
            return id;
        }
        return commitAuthorResolver.resolveByEmail(email);
    }
}
