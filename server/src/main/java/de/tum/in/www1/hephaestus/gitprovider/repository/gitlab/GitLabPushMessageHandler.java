package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.gitlab.GitLabCommitMergeRequestLinker;
import de.tum.in.www1.hephaestus.gitprovider.commit.util.CommitUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.NatsMessageDeserializer;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabEventType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto.GitLabPushEventDTO.CommitInfo;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles GitLab push webhook events.
 * <p>
 * On each push event, upserts the project as a {@link Repository}
 * using the embedded project metadata from the webhook payload. This ensures the repository
 * entity exists before any commit processing.
 * <p>
 * When local git checkout is enabled ({@code hephaestus.git.enabled=true}), pushes to the
 * default branch trigger a local clone/fetch and JGit commit walk, providing line-level
 * diff statistics (additions/deletions per file). Falls back to webhook-only processing
 * on error or for non-default branches.
 * <p>
 * Also ensures the parent group is linked as an Organization via DB lookup.
 * If the organization doesn't exist yet (push arrives before full sync), it is left unlinked
 * and will be resolved during the next scheduled sync.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabPushMessageHandler extends GitLabMessageHandler<GitLabPushEventDTO> {

    private static final Logger log = LoggerFactory.getLogger(GitLabPushMessageHandler.class);
    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

    private final GitLabProjectProcessor projectProcessor;
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final CommitRepository commitRepository;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;
    private final GitRepositoryManager gitRepositoryManager;
    private final GitLabTokenService tokenService;
    private final CommitAuthorResolver authorResolver;
    private final ScopeIdResolver scopeIdResolver;
    private final SyncTargetProvider syncTargetProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final GitLabCommitMergeRequestLinker commitMergeRequestLinker;

    GitLabPushMessageHandler(
        GitLabProjectProcessor projectProcessor,
        OrganizationRepository organizationRepository,
        RepositoryRepository repositoryRepository,
        CommitRepository commitRepository,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties,
        GitRepositoryManager gitRepositoryManager,
        GitLabTokenService tokenService,
        CommitAuthorResolver authorResolver,
        ScopeIdResolver scopeIdResolver,
        SyncTargetProvider syncTargetProvider,
        ApplicationEventPublisher eventPublisher,
        GitLabCommitMergeRequestLinker commitMergeRequestLinker,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(GitLabPushEventDTO.class, deserializer, transactionTemplate);
        this.projectProcessor = projectProcessor;
        this.organizationRepository = organizationRepository;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
        this.gitRepositoryManager = gitRepositoryManager;
        this.tokenService = tokenService;
        this.authorResolver = authorResolver;
        this.scopeIdResolver = scopeIdResolver;
        this.syncTargetProvider = syncTargetProvider;
        this.eventPublisher = eventPublisher;
        this.commitMergeRequestLinker = commitMergeRequestLinker;
    }

    @Override
    public GitLabEventType getEventType() {
        return GitLabEventType.PUSH;
    }

    @Override
    protected void handleEvent(GitLabPushEventDTO event) {
        if (event.project() == null) {
            log.warn("Received push event with missing project data");
            return;
        }

        String projectPath = event.project().pathWithNamespace();
        String safeProjectPath = sanitizeForLog(projectPath);

        if (event.isBranchDeletion()) {
            log.debug("Skipped push event: reason=branchDeletion, projectPath={}", safeProjectPath);
            return;
        }

        String safeRef = sanitizeForLog(event.ref());
        log.info(
            "Received push event: projectPath={}, ref={}, commits={}",
            safeProjectPath,
            safeRef,
            event.totalCommitsCount()
        );

        // Upsert the project as a Repository entity from the webhook payload.
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, gitLabProperties.defaultServerUrl())
            .orElseThrow(() ->
                new IllegalStateException(
                    "GitProvider not found for type=GITLAB, serverUrl=" + gitLabProperties.defaultServerUrl()
                )
            );
        var repository = projectProcessor.processPushEvent(event.project(), provider);

        if (repository != null) {
            log.debug(
                "Upserted project from push event: projectPath={}, repoId={}",
                safeProjectPath,
                repository.getId()
            );
            ensureOrganizationLinked(repository, projectPath, provider);

            Long scopeId = resolveScopeId(repository);

            // Decide between local git enrichment and webhook-only processing.
            // Local git provides line-level diff stats (additions/deletions per file).
            // Full enrichment (clone + commit walk) only for default branch pushes.
            // But we ALWAYS fetch on any branch push if the repo is already cloned,
            // so the practice review pipeline has fresh refs for diff computation.
            if (event.isDefaultBranch() && gitRepositoryManager.isEnabled()) {
                boolean scopeActive = scopeId != null && syncTargetProvider.isScopeActiveForSync(scopeId);

                if (scopeActive) {
                    processCommitsViaLocalGit(event, repository, scopeId);
                } else {
                    log.debug("Skipped local git: reason=scopeNotActive, scopeId={}", scopeId);
                    processCommitsViaWebhook(event, repository, false);
                }
            } else {
                // Non-default branch push: still fetch if the repo is already cloned
                // so practice reviews have fresh refs for diff computation.
                if (gitRepositoryManager.isEnabled() && gitRepositoryManager.isRepositoryCloned(repository.getId())) {
                    fetchForNonDefaultBranch(event, repository);
                }
                processCommitsViaWebhook(event, repository, false);
            }

            linkCommitsToMergeRequests(scopeId, repository);
        } else {
            log.warn("Failed to upsert project from push event: projectPath={}", safeProjectPath);
        }
    }

    /**
     * Runs one batched commit→MR linker pass covering MRs updated in the push window.
     * A single GraphQL round trip replaces the per-commit call that previously ran
     * inside {@link #publishCommitCreated}, collapsing N calls per push into 1.
     */
    private void linkCommitsToMergeRequests(@Nullable Long scopeId, Repository repository) {
        if (scopeId == null) {
            return;
        }
        try {
            commitMergeRequestLinker.linkCommits(scopeId, repository, OffsetDateTime.now().minusHours(1));
        } catch (Exception e) {
            log.debug("Push-time commit→MR link failed: repoId={}, error={}", repository.getId(), e.getMessage());
        }
    }

    /**
     * Fetch latest refs for non-default branch pushes. This keeps the local clone
     * current for practice review diff computation. Does not walk commits or
     * enrich file stats — that's only needed for default branch pushes.
     */
    private void fetchForNonDefaultBranch(GitLabPushEventDTO event, Repository repository) {
        try {
            Long scopeId = resolveScopeId(repository);
            if (scopeId == null || !syncTargetProvider.isScopeActiveForSync(scopeId)) {
                return;
            }
            String serverUrl = tokenService.resolveServerUrl(scopeId);
            String token = tokenService.getAccessToken(scopeId);
            String cloneUrl = serverUrl + "/" + repository.getNameWithOwner() + ".git";
            gitRepositoryManager.ensureRepository(repository.getId(), cloneUrl, token);
            log.debug(
                "Fetched non-default branch push: ref={}, repo={}",
                sanitizeForLog(event.ref()),
                sanitizeForLog(repository.getNameWithOwner())
            );
        } catch (Exception e) {
            log.warn(
                "Non-default branch fetch failed: repo={}, error={}",
                sanitizeForLog(repository.getNameWithOwner()),
                e.getMessage()
            );
        }
    }

    // ========================================================================
    // Local git path (enriched with line-level diff stats)
    // ========================================================================

    /**
     * Process commits using local git clone/fetch via JGit.
     * Provides complete file-level change information including additions/deletions per file.
     * Falls back to webhook-only on error.
     */
    private void processCommitsViaLocalGit(GitLabPushEventDTO event, Repository repository, Long scopeId) {
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        String beforeSha = event.before();
        String afterSha = event.after();

        try {
            String serverUrl = tokenService.resolveServerUrl(scopeId);
            String token = tokenService.getAccessToken(scopeId);
            String cloneUrl = serverUrl + "/" + repository.getNameWithOwner() + ".git";

            // Clone or fetch the repository locally
            gitRepositoryManager.ensureRepository(repository.getId(), cloneUrl, token);

            // Walk commits from before→after using JGit
            List<GitRepositoryManager.CommitInfo> commitInfos = gitRepositoryManager.walkCommits(
                repository.getId(),
                isInitialPush(beforeSha) ? null : beforeSha,
                afterSha
            );

            int processed = 0;
            for (GitRepositoryManager.CommitInfo info : commitInfos) {
                if (processLocalGitCommit(info, repository, serverUrl)) {
                    processed++;
                }
            }

            log.info(
                "Processed push commits via local git: processed={}, total={}, repoName={}",
                processed,
                commitInfos.size(),
                repoName
            );
        } catch (Exception e) {
            log.error(
                "Failed to process commits via local git, falling back to webhook: repoName={}, error={}",
                repoName,
                e.getMessage()
            );
            processCommitsViaWebhook(event, repository, true);
        }
    }

    /**
     * Process a single commit from local git info with full diff statistics.
     */
    private boolean processLocalGitCommit(
        GitRepositoryManager.CommitInfo info,
        Repository repository,
        String serverUrl
    ) {
        // Fast-path: skip if already persisted
        if (commitRepository.existsByShaAndRepositoryId(info.sha(), repository.getId())) {
            return false;
        }

        Long providerId = repository.getProvider().getId();
        Long authorId = authorResolver.resolveAndBackfillByEmail(info.authorEmail(), providerId);
        Long committerId = authorResolver.resolveAndBackfillByEmail(info.committerEmail(), providerId);

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

        // Attach file changes with line-level stats
        if (!info.fileChanges().isEmpty()) {
            Commit commit = commitRepository.findByShaAndRepositoryId(info.sha(), repository.getId()).orElse(null);
            if (commit != null) {
                for (GitRepositoryManager.FileChange fc : info.fileChanges()) {
                    CommitFileChange fileChange = new CommitFileChange();
                    fileChange.setFilename(truncate(fc.filename(), 1024));
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

        publishCommitCreated(info.sha(), repository);
        return true;
    }

    // ========================================================================
    // Webhook-only path (file lists without line-level stats)
    // ========================================================================

    /**
     * Creates Commit entities from the push webhook payload.
     * <p>
     * When called as fallback after local-git failure, uses null for stats
     * to preserve richer data already persisted via COALESCE.
     *
     * @param asFallback true when called after local-git failure
     */
    private void processCommitsViaWebhook(GitLabPushEventDTO event, Repository repository, boolean asFallback) {
        List<CommitInfo> commits = event.commits();
        if (commits == null || commits.isEmpty()) {
            return;
        }

        int created = 0;
        for (CommitInfo commit : commits) {
            if (commit.id() == null || commit.id().isBlank()) {
                continue;
            }

            try {
                String sha = commit.id();
                String message = extractHeadline(commit.message(), commit.title());
                String messageBody = extractBody(commit.message());
                String htmlUrl = commit.url();
                Instant authoredAt = parseTimestamp(commit.timestamp());
                int changedFiles = commit.changedFilesCount();
                String authorEmail = commit.author() != null ? commit.author().email() : null;

                commitRepository.upsertCommit(
                    sha,
                    message,
                    messageBody,
                    htmlUrl,
                    authoredAt,
                    authoredAt,
                    asFallback ? null : 0, // additions: null preserves local-git data
                    asFallback ? null : 0, // deletions: null preserves local-git data
                    asFallback ? null : (changedFiles > 0 ? changedFiles : null),
                    Instant.now(),
                    repository.getId(),
                    null,
                    null,
                    authorEmail,
                    authorEmail
                );

                // Only persist webhook file changes when NOT a fallback
                // (fallback should preserve richer local-git file changes)
                if (!asFallback) {
                    persistWebhookFileChanges(sha, commit, repository);
                }

                publishCommitCreated(sha, repository);
                created++;
            } catch (Exception e) {
                log.warn(
                    "Failed to upsert commit: sha={}, repoId={}, error={}",
                    commit.id(),
                    repository.getId(),
                    e.getMessage()
                );
            }
        }

        if (created > 0) {
            log.info(
                "Created commits from push event: repoId={}, created={}, total={}, fallback={}",
                repository.getId(),
                created,
                commits.size(),
                asFallback
            );
        }
    }

    /**
     * Persists file changes from webhook payload (filenames + change type, no line stats).
     */
    private void persistWebhookFileChanges(String sha, CommitInfo commitInfo, Repository repository) {
        boolean hasFiles =
            (commitInfo.added() != null && !commitInfo.added().isEmpty()) ||
            (commitInfo.modified() != null && !commitInfo.modified().isEmpty()) ||
            (commitInfo.removed() != null && !commitInfo.removed().isEmpty());

        if (!hasFiles) {
            return;
        }

        Commit commitEntity = commitRepository.findByShaAndRepositoryId(sha, repository.getId()).orElse(null);
        if (commitEntity == null) {
            return;
        }

        addFileChanges(commitEntity, commitInfo.added(), CommitFileChange.ChangeType.ADDED);
        addFileChanges(commitEntity, commitInfo.modified(), CommitFileChange.ChangeType.MODIFIED);
        addFileChanges(commitEntity, commitInfo.removed(), CommitFileChange.ChangeType.REMOVED);

        commitRepository.save(commitEntity);
    }

    // ========================================================================
    // Domain event publishing
    // ========================================================================

    private void publishCommitCreated(String sha, Repository repository) {
        Commit commit = commitRepository.findByShaAndRepositoryId(sha, repository.getId()).orElse(null);
        if (commit == null) {
            return;
        }

        Long scopeId = resolveScopeId(repository);

        EventPayload.CommitData commitData = EventPayload.CommitData.from(commit);
        EventContext context = new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            scopeId,
            RepositoryRef.from(repository),
            DataSource.WEBHOOK,
            null,
            UUID.randomUUID().toString(),
            GitProviderType.GITLAB
        );

        eventPublisher.publishEvent(new DomainEvent.CommitCreated(commitData, context));
    }

    // ========================================================================
    // Scope resolution
    // ========================================================================

    @Nullable
    private Long resolveScopeId(Repository repository) {
        if (repository.getOrganization() != null) {
            String orgLogin = repository.getOrganization().getLogin();
            Long scopeId = scopeIdResolver.findScopeIdByOrgLogin(orgLogin).orElse(null);
            if (scopeId != null) {
                return scopeId;
            }
        }
        return scopeIdResolver.findScopeIdByRepositoryName(repository.getNameWithOwner()).orElse(null);
    }

    // ========================================================================
    // Organization linking
    // ========================================================================

    private void ensureOrganizationLinked(Repository repository, String projectPath, GitProvider provider) {
        if (repository.getOrganization() != null) {
            return;
        }

        String groupPath = extractGroupPath(projectPath);
        if (groupPath == null) {
            return;
        }

        Organization org = organizationRepository
            .findByLoginIgnoreCaseAndProviderId(groupPath, provider.getId())
            .orElse(null);

        if (org != null) {
            repository.setOrganization(org);
            repositoryRepository.save(repository);
            log.debug(
                "Linked org to repository: repoId={}, orgLogin={}",
                repository.getId(),
                sanitizeForLog(groupPath)
            );
        } else {
            log.debug("Organization not yet synced: groupPath={}", sanitizeForLog(groupPath));
        }
    }

    // ========================================================================
    // Utility methods
    // ========================================================================

    @Nullable
    static String extractGroupPath(@Nullable String projectPath) {
        if (projectPath == null || projectPath.isBlank()) return null;
        int lastSlash = projectPath.lastIndexOf('/');
        return lastSlash <= 0 ? null : projectPath.substring(0, lastSlash);
    }

    private static String extractHeadline(@Nullable String fullMessage, @Nullable String title) {
        if (title != null && !title.isBlank()) return truncate(title, 1024);
        if (fullMessage == null || fullMessage.isBlank()) return "(no message)";
        int newline = fullMessage.indexOf('\n');
        String headline = newline > 0 ? fullMessage.substring(0, newline).trim() : fullMessage.trim();
        return truncate(headline, 1024);
    }

    @Nullable
    private static String extractBody(@Nullable String fullMessage) {
        if (fullMessage == null) return null;
        int newline = fullMessage.indexOf('\n');
        if (newline < 0 || newline + 1 >= fullMessage.length()) return null;
        String body = fullMessage.substring(newline + 1).trim();
        return body.isEmpty() ? null : body;
    }

    private static Instant parseTimestamp(@Nullable String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return Instant.now();
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private static void addFileChanges(
        Commit commit,
        @Nullable List<String> filenames,
        CommitFileChange.ChangeType changeType
    ) {
        if (filenames == null) return;
        for (String filename : filenames) {
            if (filename == null || filename.isBlank()) continue;
            CommitFileChange fc = new CommitFileChange();
            fc.setFilename(truncate(filename, 1024));
            fc.setChangeType(changeType);
            commit.addFileChange(fc);
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isInitialPush(String sha) {
        return sha == null || ZERO_SHA.equals(sha);
    }
}
