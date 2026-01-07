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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter that bridges workspace entities to the sync engine SPI.
 * <p>
 * Implements the full sync provider interface hierarchy:
 * <ul>
 *   <li>{@link SyncTargetProvider} - Core sync target operations (extends the sub-interfaces below)</li>
 *   <li>{@link de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceSyncMetadataProvider WorkspaceSyncMetadataProvider} - Workspace-level sync metadata</li>
 *   <li>{@link de.tum.in.www1.hephaestus.gitprovider.common.spi.BackfillStateProvider BackfillStateProvider} - Backfill state management</li>
 * </ul>
 */
@Component
public class WorkspaceSyncTargetProvider implements SyncTargetProvider {

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
    public List<SyncTarget> getSyncTargetsForWorkspace(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
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
    @Transactional
    public void updateSyncTimestamp(
        Long workspaceId,
        String repositoryNameWithOwner,
        SyncType syncType,
        Instant syncedAt
    ) {
        workspaceRepository
            .findById(workspaceId)
            .ifPresent(ws -> {
                ws
                    .getRepositoriesToMonitor()
                    .stream()
                    .filter(rtm -> rtm.getNameWithOwner().equals(repositoryNameWithOwner))
                    .findFirst()
                    .ifPresent(rtm -> {
                        switch (syncType) {
                            case LABELS -> rtm.setLabelsSyncedAt(syncedAt);
                            case MILESTONES -> rtm.setMilestonesSyncedAt(syncedAt);
                            case ISSUES_AND_PULL_REQUESTS -> rtm.setIssuesAndPullRequestsSyncedAt(syncedAt);
                            case COLLABORATORS -> rtm.setCollaboratorsSyncedAt(syncedAt);
                            case FULL_REPOSITORY -> rtm.setRepositorySyncedAt(syncedAt);
                            default -> {}
                        }
                        workspaceRepository.save(ws);
                    });
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceSyncMetadata> getWorkspaceSyncMetadata(Long workspaceId) {
        return workspaceRepository.findById(workspaceId).map(this::toWorkspaceSyncMetadata);
    }

    @Override
    @Transactional
    public void updateWorkspaceSyncTimestamp(Long workspaceId, SyncType syncType, Instant syncedAt) {
        workspaceRepository
            .findById(workspaceId)
            .ifPresent(ws -> {
                switch (syncType) {
                    case ISSUE_TYPES -> ws.setIssueTypesSyncedAt(syncedAt);
                    case ISSUE_DEPENDENCIES -> ws.setIssueDependenciesSyncedAt(syncedAt);
                    case SUB_ISSUES -> ws.setSubIssuesSyncedAt(syncedAt);
                    default -> {}
                }
                workspaceRepository.save(ws);
            });
    }

    private WorkspaceSyncMetadata toWorkspaceSyncMetadata(Workspace workspace) {
        // Derive organization login: prefer the linked organization, fall back to accountLogin.
        // The accountLogin is always the GitHub account (org or user) that owns the repositories,
        // so it's safe to use for organization-level operations like team sync.
        // For user accounts, the team sync will simply find no teams (which is expected).
        String orgLogin = workspace.getOrganization() != null
            ? workspace.getOrganization().getLogin()
            : workspace.getAccountLogin();

        return new WorkspaceSyncMetadata(
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
    // WORKSPACE-LEVEL USER AND TEAM SYNC
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Optional<WorkspaceUserSyncMetadata> getWorkspaceUserSyncMetadata(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(ws -> new WorkspaceUserSyncMetadata(ws.getId(), ws.getUsersSyncedAt()));
    }

    @Override
    @Transactional
    public void updateUsersSyncTimestamp(Long workspaceId, Instant syncedAt) {
        workspaceRepository
            .findById(workspaceId)
            .ifPresent(ws -> {
                ws.setUsersSyncedAt(syncedAt);
                workspaceRepository.save(ws);
            });
    }

    @Override
    public Optional<WorkspaceTeamSyncMetadata> getWorkspaceTeamSyncMetadata(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(ws -> {
                var orgNames = ws
                    .getRepositoriesToMonitor()
                    .stream()
                    .map(RepositoryToMonitor::getNameWithOwner)
                    .map(s -> s.split("/")[0])
                    .distinct()
                    .toList();
                return new WorkspaceTeamSyncMetadata(ws.getId(), ws.getTeamsSyncedAt(), orgNames);
            });
    }

    @Override
    @Transactional
    public void updateTeamsSyncTimestamp(Long workspaceId, Instant syncedAt) {
        workspaceRepository
            .findById(workspaceId)
            .ifPresent(ws -> {
                ws.setTeamsSyncedAt(syncedAt);
                workspaceRepository.save(ws);
            });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC TARGET OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
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
            .ifPresent(rtm -> {
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
            });
    }

    @Override
    @Transactional
    public void removeSyncTarget(Long syncTargetId) {
        repositoryToMonitorRepository.deleteById(syncTargetId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WORKSPACE SYNC SESSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<WorkspaceSyncSession> getWorkspaceSyncSessions() {
        List<Workspace> allWorkspaces = workspaceRepository.findAll();

        return allWorkspaces
            .stream()
            .filter(ws -> ws.getStatus() == WorkspaceStatus.ACTIVE)
            .filter(workspaceScopeFilter::isWorkspaceAllowed)
            .map(this::toWorkspaceSyncSession)
            .toList();
    }

    @Override
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

    private WorkspaceSyncSession toWorkspaceSyncSession(Workspace workspace) {
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

        return new WorkspaceSyncSession(
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
