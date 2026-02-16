package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.OrganizationSyncedEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.GitHubOrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.github.GitHubProjectSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.github.GitHubCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.exception.SyncInterruptedException;
import de.tum.in.www1.hephaestus.gitprovider.sync.exception.SyncRetriesExhaustedException;
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * GraphQL-based data synchronization service.
 *
 * <h2>Purpose</h2>
 * Orchestrates synchronization of all GitHub data using typed GraphQL models.
 * Uses the {@link SyncTargetProvider} SPI to decouple from consuming module entities.
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe. All methods can be called from multiple threads:
 * <ul>
 *   <li>{@code syncSyncTargetAsync()} submits work to the virtual thread executor</li>
 *   <li>{@code syncSyncTarget()} is safe for concurrent calls (each operates on independent data)</li>
 *   <li>{@code syncAllRepositories()} synchronizes on scope level (one call per scope)</li>
 * </ul>
 * Note: Underlying sync services must also be thread-safe.
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code monitoring.sync-cooldown-in-minutes} - Minimum interval between syncs for the same target</li>
 * </ul>
 *
 * @see SyncTargetProvider
 * @see GitHubDataSyncScheduler
 */
@Service
public class GitHubDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubDataSyncService.class);

    private final SyncSchedulerProperties syncSchedulerProperties;

    private final SyncTargetProvider syncTargetProvider;
    private final OrganizationMembershipListener organizationMembershipListener;
    private final RepositoryRepository repositoryRepository;
    private final OrganizationRepository organizationRepository;

    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;
    private final GitHubIssueTypeSyncService issueTypeSyncService;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubTeamSyncService teamSyncService;
    private final GitHubProjectSyncService projectSyncService;
    private final GitHubOrganizationSyncService organizationSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubCollaboratorSyncService collaboratorSyncService;
    private final GitHubExceptionClassifier exceptionClassifier;
    private final InstallationTokenProvider tokenProvider;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final RateLimitTracker rateLimitTracker;

    private final AsyncTaskExecutor monitoringExecutor;

    public GitHubDataSyncService(
        SyncSchedulerProperties syncSchedulerProperties,
        SyncTargetProvider syncTargetProvider,
        OrganizationMembershipListener organizationMembershipListener,
        RepositoryRepository repositoryRepository,
        OrganizationRepository organizationRepository,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueSyncService issueSyncService,
        GitHubIssueDependencySyncService issueDependencySyncService,
        GitHubIssueTypeSyncService issueTypeSyncService,
        GitHubSubIssueSyncService subIssueSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubTeamSyncService teamSyncService,
        GitHubProjectSyncService projectSyncService,
        GitHubOrganizationSyncService organizationSyncService,
        GitHubRepositorySyncService repositorySyncService,
        GitHubCollaboratorSyncService collaboratorSyncService,
        GitHubExceptionClassifier exceptionClassifier,
        InstallationTokenProvider tokenProvider,
        GitHubAppTokenService gitHubAppTokenService,
        RateLimitTracker rateLimitTracker,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.syncTargetProvider = syncTargetProvider;
        this.organizationMembershipListener = organizationMembershipListener;
        this.repositoryRepository = repositoryRepository;
        this.organizationRepository = organizationRepository;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.issueDependencySyncService = issueDependencySyncService;
        this.issueTypeSyncService = issueTypeSyncService;
        this.subIssueSyncService = subIssueSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.teamSyncService = teamSyncService;
        this.projectSyncService = projectSyncService;
        this.organizationSyncService = organizationSyncService;
        this.repositorySyncService = repositorySyncService;
        this.collaboratorSyncService = collaboratorSyncService;
        this.exceptionClassifier = exceptionClassifier;
        this.tokenProvider = tokenProvider;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.rateLimitTracker = rateLimitTracker;
        this.monitoringExecutor = monitoringExecutor;
    }

    /**
     * Syncs a sync target asynchronously using GraphQL.
     *
     * @param syncTarget the sync target to sync
     */
    public void syncSyncTargetAsync(SyncTarget syncTarget) {
        monitoringExecutor.submit(() -> syncSyncTarget(syncTarget));
    }

    /**
     * Syncs a sync target using GraphQL API.
     *
     * @param syncTarget the sync target to sync
     */
    public void syncSyncTarget(SyncTarget syncTarget) {
        Long scopeId = syncTarget.scopeId();
        String nameWithOwner = syncTarget.repositoryNameWithOwner();
        String safeNameWithOwner = sanitizeForLog(nameWithOwner);

        // Check if scope is active before attempting sync
        if (!syncTargetProvider.isScopeActiveForSync(scopeId)) {
            log.debug("Skipped sync: reason=scopeNotActive, scopeId={}, repoName={}", scopeId, safeNameWithOwner);
            return;
        }

        Repository repository = repositoryRepository.findByNameWithOwner(nameWithOwner).orElse(null);
        boolean repositoryCreatedDuringSync = false;

        // If repository doesn't exist locally, try to fetch and create it from GitHub
        // This is needed for PAT workspaces where only RepositoryToMonitor entries exist initially
        if (repository == null) {
            log.debug(
                "Repository not found locally, fetching from GitHub: scopeId={}, repoName={}",
                scopeId,
                safeNameWithOwner
            );
            var syncedRepository = repositorySyncService.syncRepository(scopeId, nameWithOwner);
            if (syncedRepository.isEmpty()) {
                log.debug(
                    "Skipped sync: reason=repositoryNotFoundOnGitHub, scopeId={}, repoName={}",
                    scopeId,
                    safeNameWithOwner
                );
                // Repository doesn't exist on GitHub - remove sync target to stop perpetual retries
                syncTargetProvider.removeSyncTarget(syncTarget.id());
                return;
            }
            repository = syncedRepository.get();
            repositoryCreatedDuringSync = true;
            syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.FULL_REPOSITORY, Instant.now());
        }

        Long repositoryId = repository.getId();

        log.info(
            "Starting repository sync: scopeId={}, repoId={}, repoName={}",
            scopeId,
            repositoryId,
            safeNameWithOwner
        );

        try {
            // P3 optimization: capture the previously-stored GitHub updatedAt before re-syncing
            // metadata. If updatedAt hasn't changed after re-sync, the repository has no new
            // activity (pushes, issues, PRs, labels, etc.) — we can skip all sub-syncs.
            Instant previousUpdatedAt = repository.getUpdatedAt();

            // Sync repository metadata (skip if we just created it above)
            if (!repositoryCreatedDuringSync) {
                var syncedRepository = repositorySyncService.syncRepository(scopeId, nameWithOwner);
                if (syncedRepository.isPresent()) {
                    repository = syncedRepository.get();
                    log.debug("Synced repository metadata: scopeId={}, repoId={}", scopeId, repositoryId);
                    syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.FULL_REPOSITORY, Instant.now());
                } else {
                    log.warn(
                        "Failed to sync repository metadata, continuing: scopeId={}, repoId={}",
                        scopeId,
                        repositoryId
                    );
                }
            }

            // P3 optimization: skip sub-syncs when the repository has no new activity.
            // GitHub's repository.updatedAt changes on any activity (pushes, issues, PRs, labels, etc.).
            // If the freshly-fetched updatedAt equals what we previously stored, nothing has changed.
            // Only apply this skip when we have a previous timestamp (not on first sync).
            boolean repoUnchanged =
                !repositoryCreatedDuringSync &&
                previousUpdatedAt != null &&
                repository.getUpdatedAt() != null &&
                !repository.getUpdatedAt().isAfter(previousUpdatedAt);

            if (repoUnchanged) {
                log.info(
                    "Skipped sub-syncs: reason=repoUnchanged, repoId={}, repoName={}, updatedAt={}",
                    repositoryId,
                    safeNameWithOwner,
                    repository.getUpdatedAt()
                );
                return;
            }

            // Sync collaborators (with cooldown)
            int collaboratorsCount = syncCollaboratorsIfNeeded(syncTarget, scopeId, repositoryId);

            // Sync labels (with cooldown — labels rarely change)
            int labelsCount = syncLabelsIfNeeded(syncTarget, scopeId, repositoryId);

            // Sync milestones (with cooldown — milestones rarely change)
            int milestonesCount = syncMilestonesIfNeeded(syncTarget, scopeId, repositoryId);

            // Sync issues (with cursor persistence for resumability)
            // Comments are synced inline with issues via the GraphQL query
            SyncResult issueResult = issueSyncService.syncForRepository(
                scopeId,
                repositoryId,
                syncTarget.id(),
                syncTarget.issueSyncCursor(),
                syncTarget.lastIssuesSyncedAt()
            );

            // Sync pull requests (with cursor persistence for resumability)
            // Review threads and comments are synced inline with PRs via the GraphQL query
            SyncResult prResult = pullRequestSyncService.syncForRepository(
                scopeId,
                repositoryId,
                syncTarget.id(),
                syncTarget.pullRequestSyncCursor(),
                syncTarget.lastPullRequestsSyncedAt()
            );

            // Update timestamps independently — each sync type tracks its own progress
            // so completed work isn't wasted when the other sync type hits rate limits
            if (issueResult.isCompleted()) {
                syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.ISSUES, Instant.now());
            } else {
                log.info(
                    "Skipped issue timestamp update due to incomplete sync: scopeId={}, repoId={}, issueStatus={}",
                    scopeId,
                    repositoryId,
                    issueResult.status()
                );
            }
            if (prResult.isCompleted()) {
                syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.PULL_REQUESTS, Instant.now());
            } else {
                log.info(
                    "Skipped PR timestamp update due to incomplete sync: scopeId={}, repoId={}, prStatus={}",
                    scopeId,
                    repositoryId,
                    prResult.status()
                );
            }

            log.info(
                "Completed repository sync: scopeId={}, repoId={}, collaborators={}, labels={}, milestones={}, issues={}, prs={}, issueStatus={}, prStatus={}",
                scopeId,
                repositoryId,
                collaboratorsCount >= 0 ? collaboratorsCount : "skipped",
                labelsCount >= 0 ? labelsCount : "skipped",
                milestonesCount >= 0 ? milestonesCount : "skipped",
                issueResult.count(),
                prResult.count(),
                issueResult.status(),
                prResult.status()
            );
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            Category category = classification.category();

            switch (category) {
                case NOT_FOUND -> {
                    log.warn(
                        "Repository sync skipped - resource not found, cleaning up orphan: scopeId={}, repoId={}, repoName={}, error={}",
                        scopeId,
                        repositoryId,
                        safeNameWithOwner,
                        classification.message()
                    );
                    // Clean up the orphaned repository to prevent permanent sync errors
                    // The repository no longer exists on GitHub, so delete it locally
                    cleanupOrphanedRepository(repositoryId, safeNameWithOwner);
                    // Also remove the sync target to stop perpetual retries
                    syncTargetProvider.removeSyncTarget(syncTarget.id());
                }
                case AUTH_ERROR -> log.error(
                    "Repository sync failed - authentication error: scopeId={}, repoId={}, error={}",
                    scopeId,
                    repositoryId,
                    classification.message()
                );
                case RATE_LIMITED -> {
                    log.warn(
                        "Repository sync failed - rate limited: scopeId={}, repoId={}, error={}",
                        scopeId,
                        repositoryId,
                        classification.message()
                    );
                    // Honor the rate limit - wait before continuing
                    Duration waitTime = classification.suggestedWait();
                    if (waitTime != null && !waitTime.isZero()) {
                        log.info(
                            "Pausing sync for rate limit: scopeId={}, waitSeconds={}",
                            scopeId,
                            waitTime.getSeconds()
                        );
                        try {
                            Thread.sleep(waitTime.toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new SyncInterruptedException("Sync interrupted while waiting for rate limit", ie);
                        }
                    }
                }
                case RETRYABLE -> log.warn(
                    "Repository sync failed - transient error: scopeId={}, repoId={}, error={}",
                    scopeId,
                    repositoryId,
                    classification.message()
                );
                default -> log.error(
                    "Failed to sync repository: scopeId={}, repoId={}, error={}",
                    scopeId,
                    repositoryId,
                    classification.message(),
                    e
                );
            }
        }
    }

    /**
     * Syncs all repositories for a scope using GraphQL.
     * <p>
     * This method orchestrates the sync in the correct order:
     * <ol>
     *   <li>Organization + memberships (users must exist first)</li>
     *   <li>Issue types (organization-level, must exist before issues are synced)</li>
     *   <li>Projects (org-level metadata before issue/PR sync to link items)</li>
     *   <li>Per-repository syncs (creates repository records, syncs issues/PRs)</li>
     *   <li>Teams + team repository permissions (requires repos to exist)</li>
     *   <li>Issue dependencies (requires issues to exist)</li>
     *   <li>Sub-issues (requires issues to exist)</li>
     * </ol>
     *
     * @param scopeId the scope ID
     */
    public void syncAllRepositories(Long scopeId) {
        // Fail-fast for suspended installations - don't spawn ANY threads
        Long installationId = tokenProvider.getInstallationId(scopeId).orElse(null);
        if (installationId != null && gitHubAppTokenService.isInstallationMarkedSuspended(installationId)) {
            log.info(
                "Skipped all repository syncs: reason=installationSuspended, scopeId={}, installationId={}",
                scopeId,
                installationId
            );
            return;
        }

        // Check if scope is active before attempting sync
        if (!syncTargetProvider.isScopeActiveForSync(scopeId)) {
            log.debug("Skipped scope sync: reason=scopeNotActive, scopeId={}", scopeId);
            return;
        }

        var syncTargets = new ArrayList<>(syncTargetProvider.getSyncTargetsForScope(scopeId));
        if (syncTargets.isEmpty()) {
            // DEBUG level: empty sync targets is expected for newly created or PAT workspaces
            // without repositories configured yet. Not an error condition.
            log.debug("Skipped scope sync: reason=noSyncTargets, scopeId={}", scopeId);
            return;
        }

        // Prioritize repos by staleness: never-synced first, then oldest sync timestamp.
        // When rate limit budget runs out mid-sync, the most stale repos have already been handled.
        syncTargets.sort(
            Comparator.comparing((SyncTarget t) -> {
                Instant issues = t.lastIssuesSyncedAt();
                Instant prs = t.lastPullRequestsSyncedAt();
                if (issues == null || prs == null) {
                    return Instant.MIN; // never-synced repos go first
                }
                // Use the older of the two timestamps (the more stale dimension)
                return issues.isBefore(prs) ? issues : prs;
            })
        );

        log.info("Starting scope sync: scopeId={}, repoCount={}", scopeId, syncTargets.size());

        try {
            // Sync organization and memberships first (users need to exist for later syncs)
            syncOrganizationAndMemberships(scopeId);

            // Sync issue types BEFORE repositories (issue types are organization-level
            // and must exist before issues are synced so they can be linked immediately)
            syncIssueTypes(scopeId);

            // Sync projects BEFORE repositories so embedded project items can be linked
            syncProjects(scopeId);

            // Sync each repository (creates repository records, issues, PRs)
            int reposProcessed = 0;
            for (SyncTarget target : syncTargets) {
                // Check if installation became suspended mid-sync - abort remaining syncs
                if (installationId != null && gitHubAppTokenService.isInstallationMarkedSuspended(installationId)) {
                    log.info("Aborting remaining syncs: reason=installationSuspended, scopeId={}", scopeId);
                    break;
                }
                // Wait for rate limit reset instead of aborting — ensures all repos
                // get their initial sync even when rate limit is exhausted mid-loop.
                // Without this, repos skipped here would have NULL issues_synced_at/
                // pull_requests_synced_at and be ineligible for historical backfill
                // until the next daily cron run.
                if (rateLimitTracker.isCritical(scopeId)) {
                    log.info(
                        "Rate limit critical during startup sync, waiting for reset: scopeId={}, remaining={}, totalRepos={}, reposProcessed={}, reposRemaining={}",
                        scopeId,
                        rateLimitTracker.getRemaining(scopeId),
                        syncTargets.size(),
                        reposProcessed,
                        syncTargets.size() - reposProcessed
                    );
                    try {
                        waitForRateLimitReset(scopeId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.info(
                            "Startup sync interrupted while waiting for rate limit: scopeId={}, reposProcessed={}, reposRemaining={}",
                            scopeId,
                            reposProcessed,
                            syncTargets.size() - reposProcessed
                        );
                        break;
                    }
                }
                if (shouldSync(target)) {
                    syncSyncTarget(target);
                }
                reposProcessed++;
            }

            // Sync teams AFTER repositories exist (team repo permissions need repos to exist)
            syncTeams(scopeId);

            // Sync scope-level relationships (after all issues/PRs are synced)
            syncScopeLevelRelationships(scopeId);
        } catch (InstallationNotFoundException e) {
            log.warn(
                "Aborting scope sync: reason=installationDeleted, scopeId={}, installationId={}",
                scopeId,
                e.getInstallationId()
            );
        }
    }

    /**
     * Syncs organization information and memberships for a scope.
     * <p>
     * This must run BEFORE repositories are synced so users exist for later syncs.
     * Teams are synced separately via {@link #syncTeams(Long)} AFTER repositories
     * exist, because team repository permissions need repository records.
     *
     * @param scopeId the scope ID
     */
    private void syncOrganizationAndMemberships(Long scopeId) {
        Optional<SyncMetadata> metadataOpt = syncTargetProvider.getSyncMetadata(scopeId);
        if (metadataOpt.isEmpty()) {
            log.debug("Skipped organization sync: reason=noMetadata, scopeId={}", scopeId);
            return;
        }

        SyncMetadata metadata = metadataOpt.get();
        String organizationLogin = metadata.organizationLogin();

        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.debug("Skipped organization sync: reason=noOrgLogin, scopeId={}", scopeId);
            return;
        }

        String safeOrgLogin = sanitizeForLog(organizationLogin);

        try {
            // Sync organization and memberships
            var organization = organizationSyncService.syncOrganization(scopeId, organizationLogin);
            if (organization != null) {
                log.debug("Synced organization: scopeId={}, orgLogin={}", scopeId, safeOrgLogin);

                // Notify listener to sync scope members from organization members
                organizationMembershipListener.onOrganizationMembershipsSynced(
                    new OrganizationSyncedEvent(organization.getId(), organizationLogin)
                );
                syncTargetProvider.updateUsersSyncTimestamp(scopeId, Instant.now());
            }
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            switch (classification.category()) {
                case AUTH_ERROR -> log.error(
                    "Organization sync failed - auth error: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message()
                );
                case RATE_LIMITED -> log.warn(
                    "Organization sync failed - rate limited: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message()
                );
                default -> log.error(
                    "Failed to sync organization: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message(),
                    e
                );
            }
        }
    }

    /**
     * Syncs issue types for a scope from its GitHub organization.
     * <p>
     * This must run AFTER organization is synced (organization must exist) but
     * BEFORE repositories are synced (so issue types exist when issues are processed).
     * Issue types are organization-level entities that issues reference.
     *
     * @param scopeId the scope ID
     */
    private void syncIssueTypes(Long scopeId) {
        try {
            // Sync issue types (has internal cooldown check)
            // Wrapped with retry because the method is @Transactional and a deadlock
            // would poison the transaction, requiring a fresh invocation.
            int issueTypesCount = retryOnTransientFailure(
                () -> issueTypeSyncService.syncIssueTypesForScope(scopeId),
                "issue type sync for scopeId=" + scopeId
            );
            if (issueTypesCount >= 0) {
                log.debug("Synced issue types: scopeId={}, issueTypeCount={}", scopeId, issueTypesCount);
            } else {
                log.debug("Skipped issue types sync: reason=cooldownActive, scopeId={}", scopeId);
            }
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            log.error(
                "Failed to sync issue types: scopeId={}, category={}, error={}",
                scopeId,
                classification.category(),
                classification.message(),
                e
            );
        }
    }

    /**
     * Syncs teams, team memberships, and team repository permissions for a scope.
     * <p>
     * This must run AFTER repositories are synced because team repository
     * permissions require repository records to exist in the database.
     *
     * @param scopeId the scope ID
     */
    private void syncTeams(Long scopeId) {
        Optional<SyncMetadata> metadataOpt = syncTargetProvider.getSyncMetadata(scopeId);
        if (metadataOpt.isEmpty()) {
            log.debug("Skipped team sync: reason=noMetadata, scopeId={}", scopeId);
            return;
        }

        SyncMetadata metadata = metadataOpt.get();
        String organizationLogin = metadata.organizationLogin();

        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.debug("Skipped team sync: reason=noOrgLogin, scopeId={}", scopeId);
            return;
        }

        String safeOrgLogin = sanitizeForLog(organizationLogin);

        try {
            int teamsCount = retryOnTransientFailure(
                () -> teamSyncService.syncTeamsForOrganization(scopeId, organizationLogin),
                "team sync for scopeId=" + scopeId
            );
            log.debug("Synced teams: scopeId={}, orgLogin={}, teamCount={}", scopeId, safeOrgLogin, teamsCount);
            syncTargetProvider.updateTeamsSyncTimestamp(scopeId, Instant.now());
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            switch (classification.category()) {
                case AUTH_ERROR -> log.error(
                    "Team sync failed - auth error: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message()
                );
                case RATE_LIMITED -> log.warn(
                    "Team sync failed - rate limited: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message()
                );
                default -> log.error(
                    "Failed to sync teams: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message(),
                    e
                );
            }
        }
    }

    /**
     * Syncs GitHub Projects V2 for a scope from its GitHub organization.
     * <p>
     * This runs BEFORE repository syncs so embedded project items can be linked.
     * Projects are organization-level entities in GitHub.
     * <p>
     * Sync is performed in two phases:
     * <ol>
     *   <li>Project list sync (metadata for all projects)</li>
     *   <li>Project items sync (items for each project, with resumable cursors)</li>
     * </ol>
     *
     * @param scopeId the scope ID
     */
    private void syncProjects(Long scopeId) {
        Optional<SyncMetadata> metadataOpt = syncTargetProvider.getSyncMetadata(scopeId);
        if (metadataOpt.isEmpty()) {
            log.debug("Skipped project sync: reason=noMetadata, scopeId={}", scopeId);
            return;
        }

        SyncMetadata metadata = metadataOpt.get();
        String organizationLogin = metadata.organizationLogin();

        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.debug("Skipped project sync: reason=noOrgLogin, scopeId={}", scopeId);
            return;
        }

        String safeOrgLogin = sanitizeForLog(organizationLogin);

        try {
            // Phase 1: Sync project list (metadata only)
            SyncResult projectListResult = projectSyncService.syncProjectsForOrganization(scopeId, organizationLogin);
            log.debug(
                "Synced project list: scopeId={}, orgLogin={}, projectCount={}, status={}",
                scopeId,
                safeOrgLogin,
                projectListResult.count(),
                projectListResult.status()
            );

            // Phase 2: Sync items for each project (decoupled phase with resumable cursors)
            // Only proceed if project list sync was successful
            if (projectListResult.isCompleted()) {
                syncProjectItems(scopeId, organizationLogin);
            } else {
                log.info(
                    "Skipped project items sync: reason=projectListSyncIncomplete, scopeId={}, status={}",
                    scopeId,
                    projectListResult.status()
                );
            }
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            switch (classification.category()) {
                case AUTH_ERROR -> log.error(
                    "Project sync failed - auth error: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message()
                );
                case RATE_LIMITED -> log.warn(
                    "Project sync failed - rate limited: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message()
                );
                default -> log.error(
                    "Failed to sync projects: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message(),
                    e
                );
            }
        }
    }

    /**
     * Syncs items for all projects in an organization.
     * <p>
     * Each project's items are synced separately with its own resumable cursor,
     * allowing interrupted syncs to resume from where they left off.
     * Projects are processed in order of staleness (oldest sync first).
     *
     * @param scopeId           the scope ID
     * @param organizationLogin the organization login
     */
    private void syncProjectItems(Long scopeId, String organizationLogin) {
        String safeOrgLogin = sanitizeForLog(organizationLogin);

        // Find the organization to get its ID
        Organization organization = organizationRepository.findByLoginIgnoreCase(organizationLogin).orElse(null);
        if (organization == null) {
            log.debug("Skipped project items sync: reason=organizationNotFound, orgLogin={}", safeOrgLogin);
            return;
        }

        // Get projects needing item sync, ordered by staleness
        List<Project> projects = projectSyncService.getProjectsNeedingItemSync(organization.getId());
        if (projects.isEmpty()) {
            log.debug("Skipped project items sync: reason=noProjects, orgLogin={}", safeOrgLogin);
            return;
        }

        int totalItemsSynced = 0;
        int projectsWithItems = 0;

        for (Project project : projects) {
            try {
                SyncResult itemResult = projectSyncService.syncProjectItems(scopeId, project);
                totalItemsSynced += itemResult.count();
                if (itemResult.count() > 0) {
                    projectsWithItems++;
                }

                // If rate limited, stop processing more projects
                if (itemResult.status() == SyncResult.Status.ABORTED_RATE_LIMIT) {
                    log.info(
                        "Stopping project items sync: reason=rateLimited, scopeId={}, projectsProcessed={}",
                        scopeId,
                        projectsWithItems
                    );
                    break;
                }
            } catch (InstallationNotFoundException e) {
                throw e;
            } catch (Exception e) {
                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                log.warn(
                    "Failed to sync project items: projectId={}, error={}",
                    project.getId(),
                    classification.message()
                );
                // Continue with next project on error
            }
        }

        log.info(
            "Completed project items sync: scopeId={}, orgLogin={}, projectsWithItems={}, totalItems={}",
            scopeId,
            safeOrgLogin,
            projectsWithItems,
            totalItemsSynced
        );
    }

    /**
     * Syncs scope-level relationships that require issues and PRs to exist first.
     * <p>
     * This includes:
     * <ul>
     *   <li>Issue dependencies (blocked_by relationships)</li>
     *   <li>Sub-issues (parent-child relationships)</li>
     * </ul>
     * <p>
     * Note: Issue types are synced earlier via {@link #syncIssueTypes(Long)} because
     * they must exist BEFORE issues are synced so issues can link to them.
     * <p>
     * These sync operations have their own cooldown mechanisms.
     *
     * @param scopeId the scope ID
     */
    private void syncScopeLevelRelationships(Long scopeId) {
        // Skip if rate limit is critically low to avoid wasting API calls
        // that will all fail with rate limit errors
        if (rateLimitTracker.isCritical(scopeId)) {
            log.warn(
                "Skipped scope-level relationship sync: reason=rateLimitCritical, scopeId={}, remaining={}",
                scopeId,
                rateLimitTracker.getRemaining(scopeId)
            );
            return;
        }

        try {
            // Sync issue dependencies (has internal cooldown check)
            int dependenciesCount = issueDependencySyncService.syncDependenciesForScope(scopeId);
            if (dependenciesCount >= 0) {
                log.debug("Synced issue dependencies: scopeId={}, dependencyCount={}", scopeId, dependenciesCount);
            } else {
                log.debug("Skipped issue dependencies sync: reason=cooldownActive, scopeId={}", scopeId);
            }
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            log.error(
                "Failed to sync issue dependencies: scopeId={}, category={}, error={}",
                scopeId,
                classification.category(),
                classification.message(),
                e
            );
        }

        try {
            // Sync sub-issues (has internal cooldown check)
            int subIssuesCount = subIssueSyncService.syncSubIssuesForScope(scopeId);
            if (subIssuesCount >= 0) {
                log.debug("Synced sub-issues: scopeId={}, subIssueCount={}", scopeId, subIssuesCount);
            } else {
                log.debug("Skipped sub-issues sync: reason=cooldownActive, scopeId={}", scopeId);
            }
        } catch (InstallationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            log.error(
                "Failed to sync sub-issues: scopeId={}, category={}, error={}",
                scopeId,
                classification.category(),
                classification.message(),
                e
            );
        }
    }

    /**
     * Syncs collaborators for a repository if the cooldown has expired.
     *
     * @param syncTarget   the sync target containing cooldown timestamps
     * @param scopeId      the scope ID
     * @param repositoryId the repository ID
     * @return number of collaborators synced, or -1 if skipped due to cooldown
     */
    private int syncCollaboratorsIfNeeded(SyncTarget syncTarget, Long scopeId, Long repositoryId) {
        Instant cooldownThreshold = Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L);
        boolean shouldSync =
            syncTarget.lastCollaboratorsSyncedAt() == null ||
            syncTarget.lastCollaboratorsSyncedAt().isBefore(cooldownThreshold);

        if (!shouldSync) {
            log.debug(
                "Skipped collaborator sync: reason=cooldownActive, repoId={}, lastSyncedAt={}",
                repositoryId,
                syncTarget.lastCollaboratorsSyncedAt()
            );
            return -1;
        }

        int count = retryOnTransientFailure(
            () -> collaboratorSyncService.syncCollaboratorsForRepository(scopeId, repositoryId),
            "collaborator sync for repoId=" + repositoryId
        );

        // Update sync timestamp
        syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.COLLABORATORS, Instant.now());

        return count;
    }

    /**
     * Syncs labels for a repository if the cooldown has expired.
     *
     * @param syncTarget   the sync target containing cooldown timestamps
     * @param scopeId      the scope ID
     * @param repositoryId the repository ID
     * @return number of labels synced, or -1 if skipped due to cooldown
     */
    private int syncLabelsIfNeeded(SyncTarget syncTarget, Long scopeId, Long repositoryId) {
        Instant cooldownThreshold = Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L);
        boolean shouldSync =
            syncTarget.lastLabelsSyncedAt() == null || syncTarget.lastLabelsSyncedAt().isBefore(cooldownThreshold);

        if (!shouldSync) {
            log.debug(
                "Skipped label sync: reason=cooldownActive, repoId={}, lastSyncedAt={}",
                repositoryId,
                syncTarget.lastLabelsSyncedAt()
            );
            return -1;
        }

        int count = retryOnTransientFailure(
            () -> labelSyncService.syncLabelsForRepository(scopeId, repositoryId),
            "label sync for repoId=" + repositoryId
        );

        syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.LABELS, Instant.now());

        return count;
    }

    /**
     * Syncs milestones for a repository if the cooldown has expired.
     *
     * @param syncTarget   the sync target containing cooldown timestamps
     * @param scopeId      the scope ID
     * @param repositoryId the repository ID
     * @return number of milestones synced, or -1 if skipped due to cooldown
     */
    private int syncMilestonesIfNeeded(SyncTarget syncTarget, Long scopeId, Long repositoryId) {
        Instant cooldownThreshold = Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L);
        boolean shouldSync =
            syncTarget.lastMilestonesSyncedAt() == null ||
            syncTarget.lastMilestonesSyncedAt().isBefore(cooldownThreshold);

        if (!shouldSync) {
            log.debug(
                "Skipped milestone sync: reason=cooldownActive, repoId={}, lastSyncedAt={}",
                repositoryId,
                syncTarget.lastMilestonesSyncedAt()
            );
            return -1;
        }

        int count = retryOnTransientFailure(
            () -> milestoneSyncService.syncMilestonesForRepository(scopeId, repositoryId),
            "milestone sync for repoId=" + repositoryId
        );

        syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.MILESTONES, Instant.now());

        return count;
    }

    private boolean shouldSync(SyncTarget target) {
        Instant staleThreshold = Instant.now().minusSeconds(syncSchedulerProperties.cooldownMinutes() * 60L);
        boolean issuesStale =
            target.lastIssuesSyncedAt() == null || target.lastIssuesSyncedAt().isBefore(staleThreshold);
        boolean prsStale =
            target.lastPullRequestsSyncedAt() == null || target.lastPullRequestsSyncedAt().isBefore(staleThreshold);
        return issuesStale || prsStale;
    }

    /**
     * Cleans up an orphaned repository that no longer exists on GitHub.
     * <p>
     * This method is called when a sync fails with NOT_FOUND, indicating the repository
     * has been deleted from GitHub. We delete it locally to prevent permanent sync errors.
     *
     * @param repositoryId the repository ID
     * @param safeNameWithOwner sanitized name for logging
     */
    private void cleanupOrphanedRepository(Long repositoryId, String safeNameWithOwner) {
        try {
            repositoryRepository
                .findById(repositoryId)
                .ifPresent(repository -> {
                    repositoryRepository.delete(repository);
                    log.info(
                        "Deleted orphaned repository after NOT_FOUND: repoId={}, repoName={}",
                        repositoryId,
                        safeNameWithOwner
                    );
                });
        } catch (Exception cleanupException) {
            log.warn(
                "Failed to cleanup orphaned repository: repoId={}, repoName={}, error={}",
                repositoryId,
                safeNameWithOwner,
                cleanupException.getMessage()
            );
        }
    }

    /**
     * Maximum number of retry attempts for transient failures (e.g. deadlocks).
     * Retries happen at the caller level so each attempt starts a new transaction.
     */
    private static final int TRANSIENT_RETRY_MAX_ATTEMPTS = 3;

    /**
     * Retries a transactional sync operation on transient failures such as database deadlocks.
     * <p>
     * When a deadlock occurs inside a {@code @Transactional} service method, PostgreSQL kills
     * the deadlock victim and Spring marks the transaction rollback-only. The transaction cannot
     * be salvaged from within the transactional boundary. This method retries from the
     * <em>caller</em> level (outside the {@code @Transactional} proxy), so each retry attempt
     * starts a fresh transaction.
     * <p>
     * Non-transient exceptions (auth errors, not-found, etc.) are NOT retried and propagate
     * immediately.
     *
     * @param operation   the sync operation to execute (must be a call through a Spring proxy)
     * @param description human-readable label for logging (e.g. "collaborator sync")
     * @return the result of the operation
     * @throws InstallationNotFoundException if the installation is not found (propagated immediately)
     * @throws RuntimeException if all retry attempts are exhausted or a non-retryable error occurs
     */
    private int retryOnTransientFailure(Supplier<Integer> operation, String description) {
        Exception lastException = null;
        for (int attempt = 0; attempt < TRANSIENT_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (InstallationNotFoundException e) {
                // Never retry auth/installation errors
                throw e;
            } catch (Exception e) {
                // Determine if this failure is retryable.
                // UnexpectedRollbackException deserves special treatment: when a deadlock
                // (or any other transient DB error) occurs inside a @Transactional method,
                // PostgreSQL aborts the victim and Spring marks the transaction rollback-only.
                // The original cause is consumed by the transaction manager, so the classifier
                // cannot find the deadlock in the cause chain. Since this wrapper ONLY calls
                // @Transactional service methods, an UnexpectedRollbackException always means
                // a poisoned transaction that should be retried with a fresh one.
                boolean retryable;
                String errorDetail;
                if (e instanceof org.springframework.transaction.UnexpectedRollbackException) {
                    retryable = true;
                    errorDetail = "Transaction rolled back (likely deadlock): " + e.getMessage();
                } else {
                    ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                    retryable = classification.category() == Category.RETRYABLE;
                    errorDetail = classification.message();
                }

                if (!retryable) {
                    // Non-transient error — don't retry, propagate immediately
                    throw e;
                }
                lastException = e;
                if (attempt + 1 < TRANSIENT_RETRY_MAX_ATTEMPTS) {
                    log.warn(
                        "Transient failure in {}, retrying: attempt={}/{}, error={}",
                        description,
                        attempt + 1,
                        TRANSIENT_RETRY_MAX_ATTEMPTS,
                        errorDetail
                    );
                    try {
                        ExponentialBackoff.sleep(attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SyncInterruptedException("Interrupted while retrying " + description, ie);
                    }
                } else {
                    log.error(
                        "Transient failure in {} after all retries exhausted: attempts={}, error={}",
                        description,
                        TRANSIENT_RETRY_MAX_ATTEMPTS,
                        errorDetail
                    );
                }
            }
        }
        // All retries exhausted — throw the last exception so the outer catch can classify it
        throw new SyncRetriesExhaustedException(
            "All " + TRANSIENT_RETRY_MAX_ATTEMPTS + " retries exhausted for " + description,
            lastException
        );
    }

    /**
     * Maximum number of rate limit wait cycles before giving up.
     * At ~5 minutes per cycle, this allows up to ~75 minutes of waiting (enough for
     * one full GitHub rate limit window of 60 minutes plus buffer).
     */
    private static final int MAX_RATE_LIMIT_WAIT_CYCLES = 15;

    /**
     * Waits for the rate limit to reset before continuing sync.
     * <p>
     * Uses {@link RateLimitTracker#waitIfNeeded(Long)} which blocks until the reset
     * time passes. If the rate limit is still critical after waiting (e.g. due to
     * clock skew or stale cached values), retries up to {@link #MAX_RATE_LIMIT_WAIT_CYCLES}
     * times before giving up.
     * <p>
     * Package-visible so {@link GitHubDataSyncScheduler} can also use it.
     *
     * @param scopeId the scope to wait for
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    void waitForRateLimitReset(Long scopeId) throws InterruptedException {
        for (int cycle = 0; cycle < MAX_RATE_LIMIT_WAIT_CYCLES; cycle++) {
            rateLimitTracker.waitIfNeeded(scopeId);
            if (!rateLimitTracker.isCritical(scopeId)) {
                log.info(
                    "Rate limit recovered, resuming sync: scopeId={}, remaining={}, waitCycles={}",
                    scopeId,
                    rateLimitTracker.getRemaining(scopeId),
                    cycle + 1
                );
                return;
            }
            // Still critical — wait a bit and retry (handles edge cases like
            // clock skew or API returning lower-than-expected remaining)
            log.debug(
                "Rate limit still critical after wait, retrying: scopeId={}, remaining={}, cycle={}/{}",
                scopeId,
                rateLimitTracker.getRemaining(scopeId),
                cycle + 1,
                MAX_RATE_LIMIT_WAIT_CYCLES
            );
        }
        log.warn(
            "Rate limit wait cycles exhausted, proceeding anyway: scopeId={}, remaining={}, maxCycles={}",
            scopeId,
            rateLimitTracker.getRemaining(scopeId),
            MAX_RATE_LIMIT_WAIT_CYCLES
        );
    }
}
