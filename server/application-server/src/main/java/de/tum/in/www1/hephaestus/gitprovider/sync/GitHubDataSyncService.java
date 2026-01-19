package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.OrganizationSyncedEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.GitHubOrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.github.GitHubCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import de.tum.in.www1.hephaestus.monitoring.MonitoringProperties;
import java.time.Instant;
import java.util.Optional;
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

    private final MonitoringProperties monitoringProperties;

    private final SyncTargetProvider syncTargetProvider;
    private final OrganizationMembershipListener organizationMembershipListener;
    private final RepositoryRepository repositoryRepository;

    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubTeamSyncService teamSyncService;
    private final GitHubOrganizationSyncService organizationSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubCollaboratorSyncService collaboratorSyncService;
    private final GitHubExceptionClassifier exceptionClassifier;

    private final AsyncTaskExecutor monitoringExecutor;

    public GitHubDataSyncService(
        MonitoringProperties monitoringProperties,
        SyncTargetProvider syncTargetProvider,
        OrganizationMembershipListener organizationMembershipListener,
        RepositoryRepository repositoryRepository,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueSyncService issueSyncService,
        GitHubIssueDependencySyncService issueDependencySyncService,
        GitHubSubIssueSyncService subIssueSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubTeamSyncService teamSyncService,
        GitHubOrganizationSyncService organizationSyncService,
        GitHubRepositorySyncService repositorySyncService,
        GitHubCollaboratorSyncService collaboratorSyncService,
        GitHubExceptionClassifier exceptionClassifier,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.monitoringProperties = monitoringProperties;
        this.syncTargetProvider = syncTargetProvider;
        this.organizationMembershipListener = organizationMembershipListener;
        this.repositoryRepository = repositoryRepository;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.issueDependencySyncService = issueDependencySyncService;
        this.subIssueSyncService = subIssueSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.teamSyncService = teamSyncService;
        this.organizationSyncService = organizationSyncService;
        this.repositorySyncService = repositorySyncService;
        this.collaboratorSyncService = collaboratorSyncService;
        this.exceptionClassifier = exceptionClassifier;
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
            // Sync repository metadata (skip if we just created it above)
            if (!repositoryCreatedDuringSync) {
                var syncedRepository = repositorySyncService.syncRepository(scopeId, nameWithOwner);
                if (syncedRepository.isPresent()) {
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

            // Sync collaborators
            int collaboratorsCount = syncCollaboratorsIfNeeded(syncTarget, scopeId, repositoryId);

            // Sync labels
            int labelsCount = labelSyncService.syncLabelsForRepository(scopeId, repositoryId);
            syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.LABELS, Instant.now());

            // Sync milestones
            int milestonesCount = milestoneSyncService.syncMilestonesForRepository(scopeId, repositoryId);
            syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.MILESTONES, Instant.now());

            // Sync issues (with cursor persistence for resumability)
            // Comments are synced inline with issues via the GraphQL query
            int issuesCount = issueSyncService.syncForRepository(
                scopeId,
                repositoryId,
                syncTarget.id(),
                syncTarget.issueSyncCursor(),
                syncTarget.lastIssuesAndPullRequestsSyncedAt()
            );

            // Sync pull requests (with cursor persistence for resumability)
            // Review threads and comments are synced inline with PRs via the GraphQL query
            int prsCount = pullRequestSyncService.syncForRepository(
                scopeId,
                repositoryId,
                syncTarget.id(),
                syncTarget.pullRequestSyncCursor(),
                syncTarget.lastIssuesAndPullRequestsSyncedAt()
            );

            // Update sync timestamp via SPI
            syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.ISSUES_AND_PULL_REQUESTS, Instant.now());

            log.info(
                "Completed repository sync: scopeId={}, repoId={}, collaborators={}, labels={}, milestones={}, issues={}, prs={}",
                scopeId,
                repositoryId,
                collaboratorsCount >= 0 ? collaboratorsCount : "skipped",
                labelsCount,
                milestonesCount,
                issuesCount,
                prsCount
            );
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
            throw e;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            Category category = classification.category();

            switch (category) {
                case NOT_FOUND -> log.warn(
                    "Repository sync skipped - resource not found: scopeId={}, repoId={}, error={}",
                    scopeId,
                    repositoryId,
                    classification.message()
                );
                case AUTH_ERROR -> log.error(
                    "Repository sync failed - authentication error: scopeId={}, repoId={}, error={}",
                    scopeId,
                    repositoryId,
                    classification.message()
                );
                case RATE_LIMITED -> log.warn(
                    "Repository sync failed - rate limited: scopeId={}, repoId={}, error={}",
                    scopeId,
                    repositoryId,
                    classification.message()
                );
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
     * This method orchestrates:
     * <ol>
     *   <li>Organization sync (if organization exists)</li>
     *   <li>Team sync (if organization exists)</li>
     *   <li>Per-repository syncs (labels, milestones, issues, PRs, comments)</li>
     *   <li>Scope-level issue dependencies sync</li>
     *   <li>Scope-level sub-issues sync</li>
     * </ol>
     *
     * @param scopeId the scope ID
     */
    public void syncAllRepositories(Long scopeId) {
        // Check if scope is active before attempting sync
        if (!syncTargetProvider.isScopeActiveForSync(scopeId)) {
            log.debug("Skipped scope sync: reason=scopeNotActive, scopeId={}", scopeId);
            return;
        }

        var syncTargets = syncTargetProvider.getSyncTargetsForScope(scopeId);
        if (syncTargets.isEmpty()) {
            log.warn("Skipped scope sync: reason=noSyncTargets, scopeId={}", scopeId);
            return;
        }

        log.info("Starting scope sync: scopeId={}, repoCount={}", scopeId, syncTargets.size());

        try {
            // Sync organization and teams first (if applicable)
            syncOrganizationAndTeams(scopeId);

            // Sync each repository
            for (SyncTarget target : syncTargets) {
                if (shouldSync(target)) {
                    syncSyncTarget(target);
                }
            }

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
     * Syncs organization and teams for a scope.
     * <p>
     * Organization sync includes memberships.
     * Team sync includes team memberships.
     * After org sync, scope members are synced from organization members.
     *
     * @param scopeId the scope ID
     */
    private void syncOrganizationAndTeams(Long scopeId) {
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
            }

            // Sync teams and team memberships
            int teamsCount = teamSyncService.syncTeamsForOrganization(scopeId, organizationLogin);
            log.debug("Synced teams: scopeId={}, orgLogin={}, teamCount={}", scopeId, safeOrgLogin, teamsCount);
        } catch (InstallationNotFoundException e) {
            // Re-throw to abort the entire sync operation
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
                    "Failed to sync organization and teams: scopeId={}, orgLogin={}, error={}",
                    scopeId,
                    safeOrgLogin,
                    classification.message(),
                    e
                );
            }
        }
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
     * These sync operations have their own cooldown mechanisms.
     *
     * @param scopeId the scope ID
     */
    private void syncScopeLevelRelationships(Long scopeId) {
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
        Instant cooldownThreshold = Instant.now().minusSeconds(monitoringProperties.getSyncCooldownInMinutes() * 60L);
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

        int count = collaboratorSyncService.syncCollaboratorsForRepository(scopeId, repositoryId);

        // Update sync timestamp
        syncTargetProvider.updateSyncTimestamp(syncTarget.id(), SyncType.COLLABORATORS, Instant.now());

        return count;
    }

    private boolean shouldSync(SyncTarget target) {
        if (target.lastIssuesAndPullRequestsSyncedAt() == null) {
            return true;
        }
        return target
            .lastIssuesAndPullRequestsSyncedAt()
            .isBefore(Instant.now().minusSeconds(monitoringProperties.getSyncCooldownInMinutes() * 60L));
    }
}
