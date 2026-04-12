package de.tum.in.www1.hephaestus.gitprovider.commit.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.util.CommitUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backfills commit history for GitLab repositories via local JGit clones.
 *
 * <p>Mirrors {@link de.tum.in.www1.hephaestus.gitprovider.commit.github.GitHubCommitBackfillService}
 * to provide full diff statistics (additions, deletions, changedFiles) and file change
 * tracking — data that the GitLab REST API commit list endpoint does not provide.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Clone or fetch the repository via {@link GitRepositoryManager}</li>
 *   <li>Resolve the HEAD SHA of the default branch</li>
 *   <li>Walk new commits since the last known SHA (or full history on first backfill)</li>
 *   <li>For each commit: upsert via native SQL, attach file changes, publish events</li>
 * </ol>
 *
 * <p>When {@code hephaestus.git.enabled=false}, {@link #backfillCommits} returns
 * {@link SyncResult#completed(int) SyncResult.completed(0)} so callers can fall
 * back to the REST-based {@link GitLabCommitSyncService}.
 *
 * @see GitLabCommitSyncService
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabCommitBackfillService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCommitBackfillService.class);
    private static final int MAX_COMMITS_PER_CYCLE = 5000;

    private final GitRepositoryManager gitRepositoryManager;
    private final GitLabTokenService tokenService;
    private final CommitRepository commitRepository;
    private final CommitAuthorResolver authorResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public GitLabCommitBackfillService(
        GitRepositoryManager gitRepositoryManager,
        GitLabTokenService tokenService,
        CommitRepository commitRepository,
        CommitAuthorResolver authorResolver,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate
    ) {
        this.gitRepositoryManager = gitRepositoryManager;
        this.tokenService = tokenService;
        this.commitRepository = commitRepository;
        this.authorResolver = authorResolver;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Backfills commits for a GitLab repository from its local git clone.
     *
     * <p>Idempotent: commits already in the database are skipped via
     * {@code existsByShaAndRepositoryId} fast-path. Returns immediately with
     * count 0 when local git is disabled.
     *
     * @param scopeId    the workspace scope ID (for token resolution)
     * @param repository the repository entity
     * @return sync result with count of new commits persisted
     */
    public SyncResult backfillCommits(Long scopeId, Repository repository) {
        if (!gitRepositoryManager.isEnabled()) {
            return SyncResult.completed(0);
        }

        Long repoId = repository.getId();
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        String defaultBranch = repository.getDefaultBranch();

        if (defaultBranch == null || defaultBranch.isBlank()) {
            log.debug("Skipped commit backfill: reason=noDefaultBranch, repoId={}, repoName={}", repoId, repoName);
            return SyncResult.completed(0);
        }

        try {
            // Phase 1: Clone/fetch (outside transaction — may be slow for initial clones)
            String serverUrl = tokenService.resolveServerUrl(scopeId);
            String token = tokenService.getAccessToken(scopeId);
            String cloneUrl = serverUrl + "/" + repository.getNameWithOwner() + ".git";
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
                return SyncResult.completed(0);
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
                return SyncResult.completed(0);
            }

            // Phase 4: Walk commits
            List<GitRepositoryManager.CommitInfo> commitInfos = gitRepositoryManager.walkCommits(
                repoId,
                fromSha,
                headSha
            );

            if (commitInfos.isEmpty()) {
                return SyncResult.completed(0);
            }

            // Phase 5: Process commits (with batch limit)
            int total = commitInfos.size();
            boolean truncated = total > MAX_COMMITS_PER_CYCLE;
            List<GitRepositoryManager.CommitInfo> batch = truncated
                ? commitInfos.subList(0, MAX_COMMITS_PER_CYCLE)
                : commitInfos;

            int processed = 0;
            for (GitRepositoryManager.CommitInfo info : batch) {
                if (processCommitInfo(info, repository, scopeId, serverUrl)) {
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
            } else if (processed > 0) {
                log.info(
                    "Completed commit backfill: repoId={}, repoName={}, newCommits={}, totalWalked={}, mode={}",
                    repoId,
                    repoName,
                    processed,
                    total,
                    fromSha != null ? "incremental" : "full"
                );
            }

            return SyncResult.completed(processed);
        } catch (GitRepositoryManager.GitOperationException e) {
            log.error(
                "Commit backfill failed (git operation): repoId={}, repoName={}, error={}",
                repoId,
                repoName,
                e.getMessage()
            );
            return SyncResult.abortedError(0);
        } catch (Exception e) {
            log.error("Commit backfill failed: repoId={}, repoName={}, error={}", repoId, repoName, e.getMessage(), e);
            return SyncResult.abortedError(0);
        }
    }

    @Nullable
    private String findLatestKnownSha(Long repositoryId) {
        return commitRepository.findLatestByRepositoryId(repositoryId).map(Commit::getSha).orElse(null);
    }

    private boolean processCommitInfo(
        GitRepositoryManager.CommitInfo info,
        Repository repository,
        Long scopeId,
        String serverUrl
    ) {
        Boolean result = transactionTemplate.execute(status -> {
            if (commitRepository.existsByShaAndRepositoryId(info.sha(), repository.getId())) {
                return false;
            }

            Long providerId = repository.getProvider() != null ? repository.getProvider().getId() : null;
            Long authorId = authorResolver.resolveByEmail(info.authorEmail(), providerId);
            Long committerId = authorResolver.resolveByEmail(info.committerEmail(), providerId);

            String message = info.message() != null ? info.message() : "";
            String htmlUrl = CommitUtils.buildGitLabCommitUrl(serverUrl, repository.getNameWithOwner(), info.sha());

            commitRepository.upsertCommit(
                info.sha(),
                message,
                info.messageBody(),
                htmlUrl,
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

            publishCommitCreated(info.sha(), repository, scopeId);
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private void publishCommitCreated(String sha, Repository repository, Long scopeId) {
        Commit commit = commitRepository.findByShaAndRepositoryId(sha, repository.getId()).orElse(null);
        if (commit == null) {
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
            UUID.randomUUID().toString(),
            GitProviderType.GITLAB
        );

        eventPublisher.publishEvent(new DomainEvent.CommitCreated(commitData, context));
    }

    private static String abbreviateSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
