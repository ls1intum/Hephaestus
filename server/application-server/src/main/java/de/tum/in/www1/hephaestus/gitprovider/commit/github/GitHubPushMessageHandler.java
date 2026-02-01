package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub push webhook events for commit synchronization.
 * <p>
 * When a push event is received:
 * <ol>
 *   <li>Fetch the latest commits via local git clone/fetch</li>
 *   <li>Walk the commits between before and after SHA</li>
 *   <li>Persist commits and file changes to the database</li>
 * </ol>
 * <p>
 * Uses local git clone for complete file-level change information.
 * Falls back to API if local checkout is disabled.
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">
 *      GitHub Push Event Documentation</a>
 */
@Component
public class GitHubPushMessageHandler extends GitHubMessageHandler<GHEventPayload.Push> {

    private static final Logger log = LoggerFactory.getLogger(GitHubPushMessageHandler.class);

    private final GitRepositoryManager gitRepositoryManager;
    private final GitHubAppTokenService tokenService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final RepositoryRepository repositoryRepository;
    private final CommitRepository commitRepository;
    private final UserRepository userRepository;

    public GitHubPushMessageHandler(
        GitRepositoryManager gitRepositoryManager,
        GitHubAppTokenService tokenService,
        GitHubRepositorySyncService repositorySyncService,
        RepositoryRepository repositoryRepository,
        CommitRepository commitRepository,
        UserRepository userRepository
    ) {
        super(GHEventPayload.Push.class);
        this.gitRepositoryManager = gitRepositoryManager;
        this.tokenService = tokenService;
        this.repositorySyncService = repositorySyncService;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.PUSH;
    }

    @Override
    @Transactional
    protected void handleEvent(GHEventPayload.Push event) {
        var ghRepository = event.getRepository();
        String repoName = ghRepository != null ? sanitizeForLog(ghRepository.getFullName()) : "unknown";

        // Skip branch deletions - no commits to process
        if (event.isDeleted()) {
            log.debug("Skipped push event: reason=branchDeleted, ref={}, repoName={}", event.getRef(), repoName);
            return;
        }

        // Skip if no commits
        if (event.getCommits() == null || event.getCommits().isEmpty()) {
            log.debug("Skipped push event: reason=noCommits, ref={}, repoName={}", event.getRef(), repoName);
            return;
        }

        log.info(
            "Received push event: branch={}, commitCount={}, forced={}, repoName={}",
            getBranchName(event.getRef()),
            event.getCommits().size(),
            event.isForced(),
            repoName
        );

        // Ensure repository exists in database
        repositorySyncService.processRepository(ghRepository);

        // Get repository from database
        Repository repository = repositoryRepository.findById(ghRepository.getId()).orElse(null);
        if (repository == null) {
            log.warn("Repository not found after sync: repoId={}", ghRepository.getId());
            return;
        }

        // Only process commits to the default branch
        String defaultBranch = repository.getDefaultBranch();
        if (!isDefaultBranch(event.getRef(), defaultBranch)) {
            log.debug(
                "Skipped push event: reason=notDefaultBranch, branch={}, defaultBranch={}, repoName={}",
                getBranchName(event.getRef()),
                defaultBranch,
                repoName
            );
            return;
        }

        // Process commits
        if (gitRepositoryManager.isEnabled()) {
            processCommitsViaLocalGit(event, repository);
        } else {
            processCommitsViaWebhook(event, repository);
        }
    }

    /**
     * Process commits using local git clone/fetch.
     * Provides complete file-level change information.
     */
    private void processCommitsViaLocalGit(GHEventPayload.Push event, Repository repository) {
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        String beforeSha = event.getBefore();
        String afterSha = event.getHead();

        try {
            // Get authentication token
            String cloneUrl = "https://github.com/" + repository.getNameWithOwner() + ".git";
            String token = tokenService.getInstallationToken(event.getInstallation().getId());

            // Ensure repository is cloned/fetched
            gitRepositoryManager.ensureRepository(repository.getId(), cloneUrl, token);

            // Walk commits from before to after
            List<GitRepositoryManager.CommitInfo> commitInfos = gitRepositoryManager.walkCommits(
                repository.getId(),
                isInitialCommit(beforeSha) ? null : beforeSha,
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
                getBranchName(event.getRef()),
                repoName
            );
        } catch (Exception e) {
            log.error(
                "Failed to process commits via local git, falling back to webhook: repoName={}, error={}",
                repoName,
                e.getMessage()
            );
            // Fall back to webhook-only data
            processCommitsViaWebhook(event, repository);
        }
    }

    /**
     * Process commits using only webhook payload data.
     * Does not provide file-level change information.
     */
    private void processCommitsViaWebhook(GHEventPayload.Push event, Repository repository) {
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        int processed = 0;

        for (var webhookCommit : event.getCommits()) {
            if (commitRepository.existsByShaAndRepositoryId(webhookCommit.getSha(), repository.getId())) {
                continue;
            }

            Commit commit = new Commit();
            commit.setSha(webhookCommit.getSha());
            commit.setMessage(webhookCommit.getMessage() != null
                ? extractHeadline(webhookCommit.getMessage())
                : "");
            commit.setMessageBody(extractBody(webhookCommit.getMessage()));
            commit.setHtmlUrl(webhookCommit.getUrl() != null ? webhookCommit.getUrl().toString() : null);
            commit.setAuthoredAt(webhookCommit.getTimestamp() != null
                ? webhookCommit.getTimestamp()
                : Instant.now());
            commit.setCommittedAt(commit.getAuthoredAt());
            commit.setRepository(repository);
            commit.setLastSyncAt(Instant.now());

            // Note: Webhook doesn't provide additions/deletions at commit level
            // We can calculate from added/modified/removed file lists
            int additions = webhookCommit.getAdded() != null ? webhookCommit.getAdded().size() : 0;
            int deletions = webhookCommit.getRemoved() != null ? webhookCommit.getRemoved().size() : 0;
            int modified = webhookCommit.getModified() != null ? webhookCommit.getModified().size() : 0;
            commit.setChangedFiles(additions + deletions + modified);

            // Try to resolve author
            if (webhookCommit.getAuthor() != null && webhookCommit.getAuthor().getUsername() != null) {
                userRepository.findByLogin(webhookCommit.getAuthor().getUsername())
                    .ifPresent(commit::setAuthor);
            }

            commitRepository.save(commit);
            processed++;
        }

        log.info(
            "Processed push commits via webhook: processed={}, total={}, branch={}, repoName={}",
            processed,
            event.getCommits().size(),
            getBranchName(event.getRef()),
            repoName
        );
    }

    /**
     * Process a single commit from local git info.
     */
    private boolean processCommitInfo(GitRepositoryManager.CommitInfo info, Repository repository) {
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
        Optional<User> author = userRepository.findByEmail(info.authorEmail());
        author.ifPresent(commit::setAuthor);

        // Try to resolve committer by email
        Optional<User> committer = userRepository.findByEmail(info.committerEmail());
        committer.ifPresent(commit::setCommitter);

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

        commitRepository.save(commit);
        return true;
    }

    private String getBranchName(String ref) {
        if (ref == null) return "unknown";
        if (ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        return ref;
    }

    private boolean isDefaultBranch(String ref, String defaultBranch) {
        if (ref == null || defaultBranch == null) return false;
        String branchName = getBranchName(ref);
        return branchName.equals(defaultBranch);
    }

    private boolean isInitialCommit(String sha) {
        return sha == null || sha.equals("0000000000000000000000000000000000000000");
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
