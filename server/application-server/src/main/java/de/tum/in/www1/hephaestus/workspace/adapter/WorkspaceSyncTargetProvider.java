package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncContextProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.SyncTargetFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceScopeFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter that bridges workspace entities to the sync engine SPI.
 * <p>
 * This adapter maps workspace domain concepts to the generic SPI abstractions:
 * <ul>
 *   <li>Workspace → Scope (identified by scopeId)</li>
 *   <li>RepositoryToMonitor → SyncTarget (identified by syncTargetId)</li>
 * </ul>
 * <p>
 * Implements the full sync provider interface hierarchy:
 * <ul>
 *   <li>{@link SyncTargetProvider} - Core sync target operations</li>
 *   <li>{@link de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTimestampProvider SyncTimestampProvider} - Sync timestamp operations</li>
 *   <li>{@link de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider BackfillStateProvider} - Backfill state management</li>
 * </ul>
 */
@Component
public class WorkspaceSyncTargetProvider implements SyncTargetProvider {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSyncTargetProvider.class);

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceScopeFilter workspaceScopeFilter;

    public WorkspaceSyncTargetProvider(
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceScopeFilter workspaceScopeFilter
    ) {
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceScopeFilter = workspaceScopeFilter;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTarget> getActiveSyncTargets() {
        return workspaceRepository
            .findAll()
            .stream()
            .filter(ws -> ws.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .flatMap(ws ->
                ws
                    .getRepositoriesToMonitor()
                    .stream()
                    .map(rtm -> SyncTargetFactory.create(ws, rtm))
            )
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTarget> getSyncTargetsForScope(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(ws ->
                ws
                    .getRepositoriesToMonitor()
                    .stream()
                    .map(rtm -> SyncTargetFactory.create(ws, rtm))
                    .toList()
            )
            .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isScopeActiveForSync(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(ws -> ws.getStatus() == WorkspaceStatus.ACTIVE)
            .orElse(false);
    }

    @Override
    @Transactional
    public void updateSyncTimestamp(Long syncTargetId, SyncType syncType, Instant syncedAt) {
        repositoryToMonitorRepository
            .findById(syncTargetId)
            .ifPresentOrElse(
                rtm -> {
                    switch (syncType) {
                        case LABELS -> rtm.setLabelsSyncedAt(syncedAt);
                        case MILESTONES -> rtm.setMilestonesSyncedAt(syncedAt);
                        case ISSUES_AND_PULL_REQUESTS -> rtm.setIssuesAndPullRequestsSyncedAt(syncedAt);
                        case COLLABORATORS -> rtm.setCollaboratorsSyncedAt(syncedAt);
                        case FULL_REPOSITORY -> rtm.setRepositorySyncedAt(syncedAt);
                        default -> {}
                    }
                    repositoryToMonitorRepository.save(rtm);
                },
                () ->
                    log.debug("Skipped sync timestamp update: reason=syncTargetNotFound, syncTargetId={}", syncTargetId)
            );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SyncMetadata> getSyncMetadata(Long scopeId) {
        return workspaceRepository.findById(scopeId).map(this::toSyncMetadata);
    }

    @Override
    @Transactional
    public void updateScopeSyncTimestamp(Long scopeId, SyncType syncType, Instant syncedAt) {
        workspaceRepository
            .findById(scopeId)
            .ifPresentOrElse(
                ws -> {
                    switch (syncType) {
                        case ISSUE_TYPES -> ws.setIssueTypesSyncedAt(syncedAt);
                        case ISSUE_DEPENDENCIES -> ws.setIssueDependenciesSyncedAt(syncedAt);
                        case SUB_ISSUES -> ws.setSubIssuesSyncedAt(syncedAt);
                        default -> {}
                    }
                    workspaceRepository.save(ws);
                },
                () ->
                    log.warn("Failed to update scope sync timestamp: reason=workspaceNotFound, workspaceId={}", scopeId)
            );
    }

    private SyncMetadata toSyncMetadata(Workspace workspace) {
        // Derive organization login: prefer the linked organization, fall back to accountLogin.
        // The accountLogin is always the GitHub account (org or user) that owns the repositories,
        // so it's safe to use for organization-level operations like team sync.
        // For user accounts, the team sync will simply find no teams (which is expected).
        String orgLogin = workspace.getOrganization() != null
            ? workspace.getOrganization().getLogin()
            : workspace.getAccountLogin();

        return new SyncMetadata(
            workspace.getId(),
            workspace.getDisplayName(),
            orgLogin,
            workspace.getOrganization() != null ? workspace.getOrganization().getId() : null,
            workspace.getIssueTypesSyncedAt(),
            workspace.getIssueDependenciesSyncedAt(),
            workspace.getSubIssuesSyncedAt()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USER AND TEAM SYNC STATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSyncState> getUserSyncState(Long scopeId) {
        return workspaceRepository.findById(scopeId).map(ws -> new UserSyncState(ws.getId(), ws.getUsersSyncedAt()));
    }

    @Override
    @Transactional
    public void updateUsersSyncTimestamp(Long scopeId, Instant syncedAt) {
        workspaceRepository
            .findById(scopeId)
            .ifPresentOrElse(
                ws -> {
                    ws.setUsersSyncedAt(syncedAt);
                    workspaceRepository.save(ws);
                },
                () ->
                    log.warn("Failed to update users sync timestamp: reason=workspaceNotFound, workspaceId={}", scopeId)
            );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TeamSyncState> getTeamSyncState(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(ws -> {
                var orgNames = ws
                    .getRepositoriesToMonitor()
                    .stream()
                    .map(RepositoryToMonitor::getNameWithOwner)
                    .map(s -> s.split("/")[0])
                    .distinct()
                    .toList();
                return new TeamSyncState(ws.getId(), ws.getTeamsSyncedAt(), orgNames);
            });
    }

    @Override
    @Transactional
    public void updateTeamsSyncTimestamp(Long scopeId, Instant syncedAt) {
        workspaceRepository
            .findById(scopeId)
            .ifPresentOrElse(
                ws -> {
                    ws.setTeamsSyncedAt(syncedAt);
                    workspaceRepository.save(ws);
                },
                () ->
                    log.warn("Failed to update teams sync timestamp: reason=workspaceNotFound, workspaceId={}", scopeId)
            );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC TARGET OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Optional<SyncTarget> findSyncTargetById(Long syncTargetId) {
        return repositoryToMonitorRepository
            .findById(syncTargetId)
            .map(rtm -> {
                var workspace = rtm.getWorkspace();
                return SyncTargetFactory.create(workspace, rtm);
            });
    }

    @Override
    @Transactional
    public void updateBackfillState(Long syncTargetId, Integer highWaterMark, Integer checkpoint, Instant lastRunAt) {
        repositoryToMonitorRepository
            .findById(syncTargetId)
            .ifPresentOrElse(
                rtm -> {
                    if (highWaterMark != null) {
                        rtm.setBackfillHighWaterMark(highWaterMark);
                    }
                    if (checkpoint != null) {
                        rtm.setBackfillCheckpoint(checkpoint);
                    }
                    if (lastRunAt != null) {
                        rtm.setBackfillLastRunAt(lastRunAt);
                    }
                    repositoryToMonitorRepository.save(rtm);
                },
                () ->
                    log.warn(
                        "Failed to update backfill state: reason=syncTargetNotFound, syncTargetId={}",
                        syncTargetId
                    )
            );
    }

    @Override
    @Transactional
    public void removeSyncTarget(Long syncTargetId) {
        repositoryToMonitorRepository.deleteById(syncTargetId);
    }

    @Override
    @Transactional
    public void updateIssueSyncCursor(Long syncTargetId, String cursor) {
        repositoryToMonitorRepository
            .findById(syncTargetId)
            .ifPresentOrElse(
                rtm -> {
                    rtm.setIssueSyncCursor(cursor);
                    repositoryToMonitorRepository.save(rtm);
                },
                () ->
                    log.warn(
                        "Failed to update issue sync cursor: reason=syncTargetNotFound, syncTargetId={}",
                        syncTargetId
                    )
            );
    }

    @Override
    @Transactional
    public void updatePullRequestSyncCursor(Long syncTargetId, String cursor) {
        repositoryToMonitorRepository
            .findById(syncTargetId)
            .ifPresentOrElse(
                rtm -> {
                    rtm.setPullRequestSyncCursor(cursor);
                    repositoryToMonitorRepository.save(rtm);
                },
                () ->
                    log.warn(
                        "Failed to update PR sync cursor: reason=syncTargetNotFound, syncTargetId={}",
                        syncTargetId
                    )
            );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC SESSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<SyncSession> getSyncSessions() {
        List<Workspace> allWorkspaces = workspaceRepository.findAll();

        return allWorkspaces
            .stream()
            .filter(ws -> ws.getStatus() == WorkspaceStatus.ACTIVE)
            .filter(workspaceScopeFilter::isWorkspaceAllowed)
            .map(this::toSyncSession)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SyncStatistics getSyncStatistics() {
        List<Workspace> allWorkspaces = workspaceRepository.findAll();

        int total = allWorkspaces.size();
        int skippedByStatus = (int) allWorkspaces
            .stream()
            .filter(ws -> ws.getStatus() != WorkspaceStatus.ACTIVE)
            .count();

        List<Workspace> activeWorkspaces = allWorkspaces
            .stream()
            .filter(ws -> ws.getStatus() == WorkspaceStatus.ACTIVE)
            .toList();

        int skippedByFilter = (int) activeWorkspaces
            .stream()
            .filter(ws -> !workspaceScopeFilter.isWorkspaceAllowed(ws))
            .count();

        int activeAndAllowed = activeWorkspaces.size() - skippedByFilter;

        return new SyncStatistics(
            total,
            skippedByStatus,
            skippedByFilter,
            activeAndAllowed,
            workspaceScopeFilter.isActive()
        );
    }

    private SyncSession toSyncSession(Workspace workspace) {
        // Filter repositories by workspace scope
        List<SyncTarget> syncTargets = workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(workspaceScopeFilter::isRepositoryAllowed)
            .map(rtm -> SyncTargetFactory.create(workspace, rtm))
            .toList();

        SyncContextProvider.SyncContext syncContext = new SyncContextProvider.SyncContext(
            workspace.getId(),
            workspace.getWorkspaceSlug(),
            workspace.getDisplayName(),
            workspace.getInstallationId()
        );

        return new SyncSession(
            workspace.getId(),
            workspace.getWorkspaceSlug(),
            workspace.getDisplayName(),
            workspace.getAccountLogin(),
            workspace.getInstallationId(),
            syncTargets,
            syncContext
        );
    }
}
