package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.OrganizationMembershipListener.OrganizationSyncedEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.GitHubIssueCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.GitHubOrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.github.GitHubCollaboratorSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    private final int syncCooldownInMinutes;

    private final SyncTargetProvider syncTargetProvider;
    private final OrganizationMembershipListener organizationMembershipListener;
    private final RepositoryRepository repositoryRepository;

    private final GitHubLabelSyncService labelSyncService;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubIssueSyncService issueSyncService;
    private final GitHubIssueCommentSyncService issueCommentSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubPullRequestSyncService pullRequestSyncService;
    private final GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService;
    private final GitHubTeamSyncService teamSyncService;
    private final GitHubOrganizationSyncService organizationSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubCollaboratorSyncService collaboratorSyncService;

    private final AsyncTaskExecutor monitoringExecutor;

    public GitHubDataSyncService(
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes,
        SyncTargetProvider syncTargetProvider,
        OrganizationMembershipListener organizationMembershipListener,
        RepositoryRepository repositoryRepository,
        GitHubLabelSyncService labelSyncService,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubIssueSyncService issueSyncService,
        GitHubIssueCommentSyncService issueCommentSyncService,
        GitHubIssueDependencySyncService issueDependencySyncService,
        GitHubSubIssueSyncService subIssueSyncService,
        GitHubPullRequestSyncService pullRequestSyncService,
        GitHubPullRequestReviewCommentSyncService pullRequestReviewCommentSyncService,
        GitHubTeamSyncService teamSyncService,
        GitHubOrganizationSyncService organizationSyncService,
        GitHubRepositorySyncService repositorySyncService,
        GitHubCollaboratorSyncService collaboratorSyncService,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.syncCooldownInMinutes = syncCooldownInMinutes;
        this.syncTargetProvider = syncTargetProvider;
        this.organizationMembershipListener = organizationMembershipListener;
        this.repositoryRepository = repositoryRepository;
        this.labelSyncService = labelSyncService;
        this.milestoneSyncService = milestoneSyncService;
        this.issueSyncService = issueSyncService;
        this.issueCommentSyncService = issueCommentSyncService;
        this.issueDependencySyncService = issueDependencySyncService;
        this.subIssueSyncService = subIssueSyncService;
        this.pullRequestSyncService = pullRequestSyncService;
        this.pullRequestReviewCommentSyncService = pullRequestReviewCommentSyncService;
        this.teamSyncService = teamSyncService;
        this.organizationSyncService = organizationSyncService;
        this.repositorySyncService = repositorySyncService;
        this.collaboratorSyncService = collaboratorSyncService;
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

        Repository repository = repositoryRepository.findByNameWithOwner(nameWithOwner).orElse(null);
        if (repository == null) {
            log.warn("Skipped sync: reason=repositoryNotFound, scopeId={}, repoName={}", scopeId, safeNameWithOwner);
            return;
        }

        Long repositoryId = repository.getId();
        log.info("Starting repository sync: scopeId={}, repoId={}, repoName={}", scopeId, repositoryId, safeNameWithOwner);

        try {
            // Sync repository metadata first (ensures entity is up-to-date before syncing related data)
            var syncedRepository = repositorySyncService.syncRepository(scopeId, nameWithOwner);
            if (syncedRepository.isPresent()) {
                log.debug("Synced repository metadata: scopeId={}, repoId={}", scopeId, repositoryId);
            } else {
                log.warn("Failed to sync repository metadata, continuing: scopeId={}, repoId={}", scopeId, repositoryId);
            }

            // Sync collaborators
            int collaboratorsCount = syncCollaboratorsIfNeeded(syncTarget, scopeId, repositoryId);

            // Sync labels
            int labelsCount = labelSyncService.syncLabelsForRepository(scopeId, repositoryId);

            // Sync milestones
            int milestonesCount = milestoneSyncService.syncMilestonesForRepository(scopeId, repositoryId);

            // Sync issues
            int issuesCount = issueSyncService.syncForRepository(scopeId, repositoryId);

            // Sync issue comments (requires issues to exist)
            int issueCommentsCount = issueCommentSyncService.syncForRepository(scopeId, repositoryId);

            // Sync pull requests
            int prsCount = pullRequestSyncService.syncForRepository(scopeId, repositoryId);

            // Sync PR review comments/threads (requires PRs to exist)
            int prReviewCommentsCount = pullRequestReviewCommentSyncService.syncCommentsForRepository(
                scopeId,
                repositoryId
            );

            // Update sync timestamp via SPI
            syncTargetProvider.updateSyncTimestamp(
                syncTarget.id(),
                SyncType.ISSUES_AND_PULL_REQUESTS,
                Instant.now()
            );

            log.info(
                "Completed repository sync: scopeId={}, repoId={}, collaborators={}, labels={}, milestones={}, issues={}, issueComments={}, prs={}, prReviewComments={}",
                scopeId,
                repositoryId,
                collaboratorsCount >= 0 ? collaboratorsCount : "skipped",
                labelsCount,
                milestonesCount,
                issuesCount,
                issueCommentsCount,
                prsCount,
                prReviewCommentsCount
            );
        } catch (Exception e) {
            log.error("Failed to sync repository: scopeId={}, repoId={}", scopeId, repositoryId, e);
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
        var syncTargets = syncTargetProvider.getSyncTargetsForScope(scopeId);
        if (syncTargets.isEmpty()) {
            log.warn("Skipped scope sync: reason=noSyncTargets, scopeId={}", scopeId);
            return;
        }

        log.info("Starting scope sync: scopeId={}, repoCount={}", scopeId, syncTargets.size());

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
        } catch (Exception e) {
            log.error("Failed to sync organization and teams: scopeId={}, orgLogin={}", scopeId, safeOrgLogin, e);
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
        } catch (Exception e) {
            log.error("Failed to sync issue dependencies: scopeId={}", scopeId, e);
        }

        try {
            // Sync sub-issues (has internal cooldown check)
            int subIssuesCount = subIssueSyncService.syncSubIssuesForScope(scopeId);
            if (subIssuesCount >= 0) {
                log.debug("Synced sub-issues: scopeId={}, subIssueCount={}", scopeId, subIssuesCount);
            } else {
                log.debug("Skipped sub-issues sync: reason=cooldownActive, scopeId={}", scopeId);
            }
        } catch (Exception e) {
            log.error("Failed to sync sub-issues: scopeId={}", scopeId, e);
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
        Instant cooldownThreshold = Instant.now().minusSeconds(syncCooldownInMinutes * 60L);
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
        syncTargetProvider.updateSyncTimestamp(
            syncTarget.id(),
            SyncType.COLLABORATORS,
            Instant.now()
        );

        return count;
    }

    private boolean shouldSync(SyncTarget target) {
        if (target.lastIssuesAndPullRequestsSyncedAt() == null) {
            return true;
        }
        return target
            .lastIssuesAndPullRequestsSyncedAt()
            .isBefore(Instant.now().minusSeconds(syncCooldownInMinutes * 60L));
    }
}
