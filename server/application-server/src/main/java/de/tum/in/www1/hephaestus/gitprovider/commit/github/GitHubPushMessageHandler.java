package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitHub push webhook events for commit synchronization.
 * <p>
 * When a push event is received:
 * <ol>
 *   <li>Validates the event (skips branch deletions, empty pushes, non-default branches)</li>
 *   <li>If local git checkout is enabled: clone/fetch, walk commits, extract file changes</li>
 *   <li>Otherwise: persist commits from webhook payload data only</li>
 * </ol>
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">
 *      GitHub Push Event Documentation</a>
 */
@Component
public class GitHubPushMessageHandler extends GitHubMessageHandler<GitHubPushEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitHubPushMessageHandler.class);
    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

    private final GitRepositoryManager gitRepositoryManager;
    private final GitHubAppTokenService tokenService;
    private final RepositoryRepository repositoryRepository;
    private final CommitRepository commitRepository;
    private final UserRepository userRepository;

    public GitHubPushMessageHandler(
        GitRepositoryManager gitRepositoryManager,
        GitHubAppTokenService tokenService,
        RepositoryRepository repositoryRepository,
        CommitRepository commitRepository,
        UserRepository userRepository,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitHubPushEventDTO.class, deserializer, transactionTemplate);
        this.gitRepositoryManager = gitRepositoryManager;
        this.tokenService = tokenService;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.userRepository = userRepository;
    }

    @Override
    public GitHubEventType getEventType() {
        return GitHubEventType.PUSH;
    }

    @Override
    protected void handleEvent(GitHubPushEventDTO event) {
        String repoName = event.repository() != null ? sanitizeForLog(event.repository().fullName()) : "unknown";

        // Skip branch deletions - no commits to process
        if (event.deleted()) {
            log.debug("Skipped push event: reason=branchDeleted, ref={}, repoName={}", event.ref(), repoName);
            return;
        }

        // Skip if no commits
        if (event.commits() == null || event.commits().isEmpty()) {
            log.debug("Skipped push event: reason=noCommits, ref={}, repoName={}", event.ref(), repoName);
            return;
        }

        log.info(
            "Received push event: branch={}, commitCount={}, forced={}, repoName={}",
            getBranchName(event.ref()),
            event.commits().size(),
            event.forced(),
            repoName
        );

        // Get repository from database by its GitHub ID
        Long repoId = event.repository() != null ? event.repository().id() : null;
        if (repoId == null) {
            log.warn("Skipped push event: reason=missingRepositoryId, repoName={}", repoName);
            return;
        }

        Repository repository = repositoryRepository.findById(repoId).orElse(null);
        if (repository == null) {
            log.debug("Skipped push event: reason=repositoryNotFound, repoId={}, repoName={}", repoId, repoName);
            return;
        }

        // Only process commits to the default branch
        String defaultBranch = repository.getDefaultBranch();
        if (!isDefaultBranch(event.ref(), defaultBranch)) {
            log.debug(
                "Skipped push event: reason=notDefaultBranch, branch={}, defaultBranch={}, repoName={}",
                getBranchName(event.ref()),
                defaultBranch,
                repoName
            );
            return;
        }

        // Process commits
        if (gitRepositoryManager.isEnabled()) {
            processCommitsViaLocalGit(event, repository);
        } else {
            processCommitsViaWebhook(event, repository, false);
        }
    }

    /**
     * Process commits using local git clone/fetch.
     * Provides complete file-level change information.
     * <p>
     * KNOWN LIMITATION: {@code ensureRepository()} (which may clone a repo
     * from scratch — potentially minutes for large repos) runs inside the
     * {@link de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler}'s
     * {@code TransactionTemplate} block, holding a DB connection for the
     * entire duration. Under high push event volume with many uncached repos,
     * this could exhaust the HikariCP connection pool. In practice, repos
     * are cloned once and then only fetched (fast), limiting the impact.
     */
    private void processCommitsViaLocalGit(GitHubPushEventDTO event, Repository repository) {
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        String beforeSha = event.before();
        String afterSha = event.after();

        try {
            // Get authentication token
            String cloneUrl = "https://github.com/" + repository.getNameWithOwner() + ".git";
            String token = null;
            if (event.installation() != null && tokenService.isConfigured()) {
                token = tokenService.getInstallationToken(event.installation().id());
            }

            // Ensure repository is cloned/fetched
            gitRepositoryManager.ensureRepository(repository.getId(), cloneUrl, token);

            // Walk commits from before to after
            List<GitRepositoryManager.CommitInfo> commitInfos = gitRepositoryManager.walkCommits(
                repository.getId(),
                isInitialPush(beforeSha) ? null : beforeSha,
                afterSha
            );

            int processed = 0;
            for (GitRepositoryManager.CommitInfo info : commitInfos) {
                if (processCommitInfo(info, repository)) {
                    processed++;
                }
            }

            log.info(
                "Processed push commits via local git: processed={}, total={}, branch={}, repoName={}",
                processed,
                commitInfos.size(),
                getBranchName(event.ref()),
                repoName
            );
        } catch (Exception e) {
            log.error(
                "Failed to process commits via local git, falling back to webhook: repoName={}, error={}",
                repoName,
                e.getMessage()
            );
            // Fall back to webhook-only data (preserves any richer data already persisted)
            processCommitsViaWebhook(event, repository, true);
        }
    }

    /**
     * Process commits using only webhook payload data.
     * Does not provide file-level change information (only file lists).
     * <p>
     * Uses native {@code INSERT ... ON CONFLICT DO UPDATE} for idempotency,
     * avoiding the check-then-act race of {@code existsBy...} + {@code save}.
     * <p>
     * When called as fallback after local-git failure, uses {@code COALESCE}
     * semantics: additions/deletions/changedFiles pass null so the upsert
     * preserves any richer data already persisted by the local-git path.
     *
     * @param asFallback true when called as fallback after local-git failure,
     *                   which uses null for stats to preserve existing richer data
     */
    private void processCommitsViaWebhook(GitHubPushEventDTO event, Repository repository, boolean asFallback) {
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        int processed = 0;

        for (var webhookCommit : event.commits()) {
            String message = extractHeadline(webhookCommit.message());
            String messageBody = extractBody(webhookCommit.message());
            // webhookCommit.url() returns the API URL (api.github.com/...),
            // not the browser-facing HTML URL; build it ourselves.
            String htmlUrl = buildCommitUrl(repository.getNameWithOwner(), webhookCommit.sha());
            Instant authoredAt = webhookCommit.timestamp() != null ? webhookCommit.timestamp() : Instant.now();

            // Count changed files from the file lists
            int added = webhookCommit.added() != null ? webhookCommit.added().size() : 0;
            int removed = webhookCommit.removed() != null ? webhookCommit.removed().size() : 0;
            int modified = webhookCommit.modified() != null ? webhookCommit.modified().size() : 0;
            int changedFiles = added + removed + modified;

            // Resolve author/committer by username
            Long authorId = resolveUserIdByLogin(
                webhookCommit.author() != null ? webhookCommit.author().username() : null
            );
            Long committerId = resolveUserIdByLogin(
                webhookCommit.committer() != null ? webhookCommit.committer().username() : null
            );

            commitRepository.upsertCommit(
                webhookCommit.sha(),
                message,
                messageBody,
                htmlUrl,
                authoredAt,
                authoredAt, // committedAt = authoredAt (webhook doesn't distinguish)
                asFallback ? null : 0, // additions: null preserves existing richer data on fallback
                asFallback ? null : 0, // deletions: null preserves existing richer data on fallback
                asFallback ? null : changedFiles, // changedFiles: null preserves on fallback
                Instant.now(),
                repository.getId(),
                authorId,
                committerId
            );
            processed++;
        }

        log.info(
            "Processed push commits via webhook: processed={}, total={}, branch={}, fallback={}, repoName={}",
            processed,
            event.commits().size(),
            getBranchName(event.ref()),
            asFallback,
            repoName
        );
    }

    /**
     * Process a single commit from local git info.
     * <p>
     * Uses optimistic insert: attempts {@code save()} and catches
     * {@link DataIntegrityViolationException} from the unique constraint
     * (sha, repository_id), treating it as a benign concurrent insert.
     * This avoids the check-then-act race of {@code existsBy...} + {@code save}.
     */
    private boolean processCommitInfo(GitRepositoryManager.CommitInfo info, Repository repository) {
        // Fast-path: skip if already persisted (avoid building entity graph)
        if (commitRepository.existsByShaAndRepositoryId(info.sha(), repository.getId())) {
            return false;
        }

        Commit commit = new Commit();
        commit.setSha(info.sha());
        commit.setMessage(info.message());
        commit.setMessageBody(info.messageBody());
        commit.setHtmlUrl(buildCommitUrl(repository.getNameWithOwner(), info.sha()));
        commit.setAuthoredAt(info.authoredAt());
        commit.setCommittedAt(info.committedAt());
        commit.setAdditions(info.additions());
        commit.setDeletions(info.deletions());
        commit.setChangedFiles(info.changedFiles());
        commit.setRepository(repository);
        commit.setLastSyncAt(Instant.now());

        // Try to resolve author by email
        if (info.authorEmail() != null) {
            userRepository.findByEmail(info.authorEmail()).ifPresent(commit::setAuthor);
        }

        // Try to resolve committer by email
        if (info.committerEmail() != null) {
            userRepository.findByEmail(info.committerEmail()).ifPresent(commit::setCommitter);
        }

        // Process file changes
        for (GitRepositoryManager.FileChange fc : info.fileChanges()) {
            CommitFileChange fileChange = new CommitFileChange();
            fileChange.setFilename(fc.filename());
            fileChange.setChangeType(CommitFileChange.fromGitChangeType(fc.changeType()));
            fileChange.setAdditions(fc.additions());
            fileChange.setDeletions(fc.deletions());
            fileChange.setChanges(fc.changes());
            fileChange.setPreviousFilename(fc.previousFilename());
            fileChange.setPatch(fc.patch());
            commit.addFileChange(fileChange);
        }

        try {
            commitRepository.save(commit);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert won the race — benign, skip.
            //
            // KNOWN LIMITATION: this exception marks the enclosing Spring
            // transaction as rollback-only. Subsequent save() calls in the
            // same TransactionTemplate block will fail. In practice, this
            // race requires two instances processing the same NATS push
            // message simultaneously, which NATS consumer groups prevent.
            // The existsByShaAndRepositoryId fast-path above prevents the
            // vast majority of duplicates; this catch is a narrow safety net.
            log.debug("Commit already persisted concurrently: sha={}, repo={}", info.sha(), repository.getId());
            return false;
        }
    }

    /**
     * Resolve a user's database ID by login, returning null if not found.
     */
    @Nullable
    private Long resolveUserIdByLogin(@Nullable String login) {
        if (login == null || login.isBlank()) {
            return null;
        }
        return userRepository
            .findByLogin(login)
            .map(u -> u.getId())
            .orElse(null);
    }

    // ========== Utility Methods ==========

    private String getBranchName(String ref) {
        if (ref == null) return "unknown";
        if (ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        return ref;
    }

    private boolean isDefaultBranch(String ref, String defaultBranch) {
        if (ref == null || defaultBranch == null) return false;
        return getBranchName(ref).equals(defaultBranch);
    }

    private boolean isInitialPush(String sha) {
        return sha == null || ZERO_SHA.equals(sha);
    }

    private String extractHeadline(String message) {
        if (message == null || message.isBlank()) return "";
        int newlineIndex = message.indexOf('\n');
        if (newlineIndex > 0) {
            return message.substring(0, newlineIndex).trim();
        }
        return message.trim();
    }

    private String extractBody(String message) {
        if (message == null || message.isBlank()) return null;
        int newlineIndex = message.indexOf('\n');
        if (newlineIndex > 0 && newlineIndex < message.length() - 1) {
            return message.substring(newlineIndex + 1).trim();
        }
        return null;
    }

    private String buildCommitUrl(String nameWithOwner, String sha) {
        return "https://github.com/" + nameWithOwner + "/commit/" + sha;
    }
}
