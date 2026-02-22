package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backfills commit history from local bare git repositories during the sync cycle.
 * <p>
 * Unlike webhook-based commit ingestion which only captures pushes going forward,
 * this service walks the full commit history from the local bare clone to ensure
 * all historical commits are persisted.
 * <p>
 * <b>How it works:</b>
 * <ol>
 *   <li>Clone or fetch the repository via {@link GitRepositoryManager}</li>
 *   <li>Resolve the HEAD SHA of the default branch</li>
 *   <li>If commits already exist for this repo, find the latest known SHA
 *       and walk only new commits (incremental)</li>
 *   <li>If no commits exist, walk the entire history (initial backfill)</li>
 *   <li>For each commit: upsert via native SQL, attach file changes, publish events</li>
 * </ol>
 * <p>
 * <b>Thread Safety:</b> This service is thread-safe. Multiple calls for different
 * repositories can run concurrently. {@link GitRepositoryManager} handles per-repo
 * locking internally.
 * <p>
 * <b>Transaction Boundary:</b> This service intentionally does NOT use
 * {@code @Transactional} at the class level. Git clone/fetch operations are I/O-heavy
 * and should not hold a database connection. Individual commit upserts use the
 * repository's own {@code @Transactional} methods.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubCommitBackfillService {

    /**
     * Maximum number of commits to process per repository per backfill cycle.
     * Prevents OOM for repositories with very long histories (e.g. 100k+ commits).
     */
    private static final int MAX_COMMITS_PER_CYCLE = 5000;

    private final GitRepositoryManager gitRepositoryManager;
    private final GitHubAppTokenService tokenService;
    private final CommitRepository commitRepository;
    private final CommitAuthorResolver authorResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Backfills commits for a repository from its local bare git clone.
     * <p>
     * This method is safe to call repeatedly — it is idempotent. Commits that
     * already exist are skipped via the {@code existsByShaAndRepositoryId} fast-path.
     * <p>
     * Git clone/fetch operations run OUTSIDE any transaction to avoid holding
     * database connections during potentially slow I/O.
     *
     * @param syncTarget the sync target (provides auth info)
     * @param repository the repository entity (provides ID, name, default branch)
     * @param scopeId    the scope ID for event context
     * @return number of new commits persisted, or -1 if skipped (disabled/error)
     */
    public int backfillCommits(SyncTarget syncTarget, Repository repository, Long scopeId) {
        if (!gitRepositoryManager.isEnabled()) {
            return -1;
        }

        Long repoId = repository.getId();
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        String defaultBranch = repository.getDefaultBranch();

        if (defaultBranch == null || defaultBranch.isBlank()) {
            log.debug("Skipped commit backfill: reason=noDefaultBranch, repoId={}, repoName={}", repoId, repoName);
            return -1;
        }

        try {
            // Phase 1: Clone/fetch (OUTSIDE transaction — may be slow for initial clones)
            String cloneUrl = "https://github.com/" + repository.getNameWithOwner() + ".git";
            String token = resolveToken(syncTarget);
            gitRepositoryManager.ensureRepository(repoId, cloneUrl, token);

            // Phase 2: Resolve HEAD of default branch
            String headSha = gitRepositoryManager.resolveDefaultBranchHead(repoId, defaultBranch);
            if (headSha == null) {
                log.warn(
                    "Skipped commit backfill: reason=cannotResolveHead, repoId={}, repoName={}, branch={}",
                    repoId,
                    repoName,
                    defaultBranch
                );
                return -1;
            }

            // Phase 3: Determine walk range (incremental vs full)
            String fromSha = findLatestKnownSha(repoId);
            if (fromSha != null && fromSha.equals(headSha)) {
                log.debug(
                    "Skipped commit backfill: reason=alreadyUpToDate, repoId={}, repoName={}, headSha={}",
                    repoId,
                    repoName,
                    abbreviateSha(headSha)
                );
                return 0;
            }

            // Phase 4: Walk commits
            List<GitRepositoryManager.CommitInfo> commitInfos = gitRepositoryManager.walkCommits(
                repoId,
                fromSha,
                headSha
            );

            if (commitInfos.isEmpty()) {
                log.debug(
                    "No new commits to backfill: repoId={}, repoName={}, fromSha={}, headSha={}",
                    repoId,
                    repoName,
                    fromSha != null ? abbreviateSha(fromSha) : "null",
                    abbreviateSha(headSha)
                );
                return 0;
            }

            // Phase 5: Process commits (with batch limit)
            int total = commitInfos.size();
            boolean truncated = total > MAX_COMMITS_PER_CYCLE;
            List<GitRepositoryManager.CommitInfo> batch = truncated
                ? commitInfos.subList(0, MAX_COMMITS_PER_CYCLE)
                : commitInfos;

            int processed = 0;
            for (GitRepositoryManager.CommitInfo info : batch) {
                if (processCommitInfo(info, repository, scopeId)) {
                    processed++;
                }
            }

            if (truncated) {
                log.info(
                    "Commit backfill batch limit reached: repoId={}, repoName={}, processed={}, total={}, remaining={}",
                    repoId,
                    repoName,
                    processed,
                    total,
                    total - MAX_COMMITS_PER_CYCLE
                );
            } else {
                log.info(
                    "Completed commit backfill: repoId={}, repoName={}, newCommits={}, totalWalked={}, mode={}",
                    repoId,
                    repoName,
                    processed,
                    total,
                    fromSha != null ? "incremental" : "full"
                );
            }

            return processed;
        } catch (GitRepositoryManager.GitOperationException e) {
            log.error(
                "Commit backfill failed (git operation): repoId={}, repoName={}, error={}",
                repoId,
                repoName,
                e.getMessage()
            );
            return -1;
        } catch (Exception e) {
            log.error("Commit backfill failed: repoId={}, repoName={}, error={}", repoId, repoName, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Resolves the authentication token for git operations.
     *
     * @param syncTarget the sync target with auth info
     * @return the token, or null for public repos
     */
    @Nullable
    private String resolveToken(SyncTarget syncTarget) {
        if (syncTarget.authMode() == AuthMode.PERSONAL_ACCESS_TOKEN) {
            return syncTarget.personalAccessToken();
        }
        if (syncTarget.installationId() != null && tokenService.isConfigured()) {
            try {
                return tokenService.getInstallationToken(syncTarget.installationId());
            } catch (Exception e) {
                log.warn("Failed to get installation token for commit backfill: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * Finds the SHA of the latest known commit for a repository.
     * Used to determine the starting point for incremental backfill.
     *
     * @param repositoryId the repository ID
     * @return the latest commit SHA, or null if no commits exist
     */
    @Nullable
    private String findLatestKnownSha(Long repositoryId) {
        return commitRepository.findLatestByRepositoryId(repositoryId).map(Commit::getSha).orElse(null);
    }

    /**
     * Process a single commit from local git info.
     * <p>
     * Runs inside a {@link TransactionTemplate} to ensure the Hibernate session is active
     * for lazy collection access (e.g., file changes). Uses the same upsert pattern as
     * {@link GitHubPushMessageHandler#processCommitInfo}: native SQL INSERT...ON CONFLICT
     * for the commit row, then entity-level attachment of file changes.
     *
     * @param info       the commit info from git walk
     * @param repository the repository entity
     * @param scopeId    the scope ID for event context
     * @return true if this was a new commit, false if already existed
     */
    private boolean processCommitInfo(GitRepositoryManager.CommitInfo info, Repository repository, Long scopeId) {
        Boolean result = transactionTemplate.execute(status -> {
            // Fast-path: skip if already persisted
            if (commitRepository.existsByShaAndRepositoryId(info.sha(), repository.getId())) {
                return false;
            }

            // Resolve author/committer IDs by email (with noreply fallback)
            Long authorId = authorResolver.resolveByEmail(info.authorEmail());
            Long committerId = authorResolver.resolveByEmail(info.committerEmail());

            // Upsert commit via native SQL (no exception on conflict)
            // Defense-in-depth: git_commit.message is NOT NULL; default to empty string
            String message = info.message() != null ? info.message() : "";
            commitRepository.upsertCommit(
                info.sha(),
                message,
                info.messageBody(),
                buildCommitUrl(repository.getNameWithOwner(), info.sha()),
                info.authoredAt(),
                info.committedAt(),
                info.additions(),
                info.deletions(),
                info.changedFiles(),
                Instant.now(),
                repository.getId(),
                authorId,
                committerId,
                info.authorEmail(),
                info.committerEmail()
            );

            // Attach file changes if present
            if (!info.fileChanges().isEmpty()) {
                Commit commit = commitRepository.findByShaAndRepositoryId(info.sha(), repository.getId()).orElse(null);
                if (commit != null) {
                    for (GitRepositoryManager.FileChange fc : info.fileChanges()) {
                        CommitFileChange fileChange = new CommitFileChange();
                        fileChange.setFilename(fc.filename());
                        fileChange.setChangeType(CommitFileChange.fromGitChangeType(fc.changeType()));
                        fileChange.setAdditions(fc.additions());
                        fileChange.setDeletions(fc.deletions());
                        fileChange.setChanges(fc.changes());
                        fileChange.setPreviousFilename(fc.previousFilename());
                        commit.addFileChange(fileChange);
                    }
                    commitRepository.save(commit);
                }
            }

            // Publish CommitCreated event (fires after transaction commits)
            publishCommitCreated(info.sha(), repository, scopeId);

            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    /**
     * Publishes a {@link DomainEvent.CommitCreated} event for a newly persisted commit.
     */
    private void publishCommitCreated(String sha, Repository repository, Long scopeId) {
        Commit commit = commitRepository.findByShaAndRepositoryId(sha, repository.getId()).orElse(null);
        if (commit == null) {
            log.debug("Cannot publish CommitCreated: commit not found after upsert: sha={}", sha);
            return;
        }

        EventPayload.CommitData commitData = EventPayload.CommitData.from(commit);
        EventContext context = new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            scopeId,
            RepositoryRef.from(repository),
            DataSource.GRAPHQL_SYNC,
            null,
            UUID.randomUUID().toString()
        );

        eventPublisher.publishEvent(new DomainEvent.CommitCreated(commitData, context));
    }

    private String buildCommitUrl(String nameWithOwner, String sha) {
        return CommitUtils.buildCommitUrl(nameWithOwner, sha);
    }

    private static String abbreviateSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
