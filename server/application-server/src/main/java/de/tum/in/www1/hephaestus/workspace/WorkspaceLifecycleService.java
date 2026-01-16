package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;

import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.exception.WorkspaceLifecycleViolationException;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamLabelFilterRepository;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamRepositorySettingsRepository;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service coordinating workspace lifecycle state transitions and validation.
 * Manages suspend, resume, and purge operations with proper guardrails.
 */
@Service
public class WorkspaceLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLifecycleService.class);

    private final boolean isNatsEnabled;
    private final WorkspaceRepository workspaceRepository;
    private final NatsConsumerService natsConsumerService;

    // Repositories for workspace-scoped data cleanup
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceTeamSettingsRepository workspaceTeamSettingsRepository;
    private final WorkspaceTeamLabelFilterRepository workspaceTeamLabelFilterRepository;
    private final WorkspaceTeamRepositorySettingsRepository workspaceTeamRepositorySettingsRepository;
    private final WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository;
    private final ActivityEventRepository activityEventRepository;

    public WorkspaceLifecycleService(
        @Value("${nats.enabled}") boolean isNatsEnabled,
        WorkspaceRepository workspaceRepository,
        NatsConsumerService natsConsumerService,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceTeamSettingsRepository workspaceTeamSettingsRepository,
        WorkspaceTeamLabelFilterRepository workspaceTeamLabelFilterRepository,
        WorkspaceTeamRepositorySettingsRepository workspaceTeamRepositorySettingsRepository,
        WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository,
        ActivityEventRepository activityEventRepository
    ) {
        this.isNatsEnabled = isNatsEnabled;
        this.workspaceRepository = workspaceRepository;
        this.natsConsumerService = natsConsumerService;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceTeamSettingsRepository = workspaceTeamSettingsRepository;
        this.workspaceTeamLabelFilterRepository = workspaceTeamLabelFilterRepository;
        this.workspaceTeamRepositorySettingsRepository = workspaceTeamRepositorySettingsRepository;
        this.workspaceSlugHistoryRepository = workspaceSlugHistoryRepository;
        this.activityEventRepository = activityEventRepository;
    }

    /**
     * Suspend a workspace, preventing new sync cycles and making it read-only.
     * Idempotent: calling suspend on an already suspended workspace is a no-op.
     *
     * @param slug the workspace slug
     * @return the suspended workspace
     * @throws EntityNotFoundException if workspace does not exist
     * @throws WorkspaceLifecycleViolationException if workspace is already purged
     */
    @Transactional
    public Workspace suspendWorkspace(String workspaceSlug) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            throw new WorkspaceLifecycleViolationException("Cannot suspend a purged workspace: " + workspaceSlug);
        }

        if (workspace.getStatus() != WorkspaceStatus.SUSPENDED) {
            workspace.setStatus(WorkspaceStatus.SUSPENDED);
            workspace = workspaceRepository.save(workspace);
            log.info("Suspended workspace: workspaceSlug={}", LoggingUtils.sanitizeForLog(workspaceSlug));
            stopNatsForWorkspace(workspace);
        }

        return workspace;
    }

    @Transactional
    public Workspace suspendWorkspace(WorkspaceContext workspaceContext) {
        return suspendWorkspace(requireSlug(workspaceContext));
    }

    /**
     * Resume a suspended workspace, making it active again.
     * Idempotent: calling resume on an already active workspace is a no-op.
     *
     * @param slug the workspace slug
     * @return the resumed workspace
     * @throws EntityNotFoundException if workspace does not exist
     * @throws WorkspaceLifecycleViolationException if workspace is purged (cannot resume purged)
     */
    @Transactional
    public Workspace resumeWorkspace(String workspaceSlug) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            throw new WorkspaceLifecycleViolationException("Cannot resume a purged workspace: " + workspaceSlug);
        }

        if (workspace.getStatus() != WorkspaceStatus.ACTIVE) {
            workspace.setStatus(WorkspaceStatus.ACTIVE);
            workspace = workspaceRepository.save(workspace);
            log.info("Resumed workspace: workspaceSlug={}", LoggingUtils.sanitizeForLog(workspaceSlug));
            startNatsForWorkspace(workspace);
        }

        return workspace;
    }

    @Transactional
    public Workspace resumeWorkspace(WorkspaceContext workspaceContext) {
        return resumeWorkspace(requireSlug(workspaceContext));
    }

    /**
     * Purge a workspace by deleting all associated data and marking it as PURGED.
     *
     * <p>This performs a hard delete of all workspace-scoped data in the correct order:
     * <ol>
     *   <li><b>Stop NATS consumers</b> - Prevents race conditions during cleanup</li>
     *   <li><b>Delete workspace settings</b> - Team settings, label filters, repository settings</li>
     *   <li><b>Delete workspace memberships</b> - User-workspace associations</li>
     *   <li><b>Delete activity events</b> - Leaderboard and activity data</li>
     *   <li><b>Delete repository monitors</b> - Monitored repository configuration</li>
     *   <li><b>Delete slug history</b> - URL redirect history</li>
     *   <li><b>Unlink organization</b> - Clear the organization association</li>
     *   <li><b>Mark as PURGED</b> - Terminal state preventing reactivation</li>
     * </ol>
     *
     * <p>Idempotent: calling purge on an already purged workspace is a no-op.
     *
     * <p><b>Note:</b> This method runs in a single transaction. For very large workspaces
     * with millions of activity events, consider implementing batch deletion in a
     * separate scheduled job to avoid long-running transactions.
     *
     * @param workspaceSlug the workspace slug
     * @return the purged workspace
     * @throws EntityNotFoundException if workspace does not exist
     */
    @Transactional
    public Workspace purgeWorkspace(String workspaceSlug) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            log.debug(
                "Skipped workspace purge: reason=alreadyPurged, workspaceSlug={}",
                LoggingUtils.sanitizeForLog(workspaceSlug)
            );
            return workspace;
        }

        Long workspaceId = workspace.getId();
        String sanitizedSlug = LoggingUtils.sanitizeForLog(workspaceSlug);

        // Step 1: Stop NATS consumers FIRST to prevent race conditions
        // Must happen before any data deletion to avoid processing events for deleted entities
        stopNatsForWorkspace(workspace);
        log.debug("Stopped NATS consumer for workspace purge: workspaceId={}", workspaceId);

        // Step 2: Delete workspace settings (team settings, label filters, repository settings)
        // These have FK constraints to workspace, so delete them first
        workspaceTeamLabelFilterRepository.deleteAllByWorkspaceId(workspaceId);
        workspaceTeamRepositorySettingsRepository.deleteAllByWorkspaceId(workspaceId);
        workspaceTeamSettingsRepository.deleteAllByWorkspaceId(workspaceId);
        log.debug("Deleted workspace settings: workspaceId={}", workspaceId);

        // Step 3: Delete workspace memberships
        workspaceMembershipRepository.deleteAllByWorkspaceId(workspaceId);
        log.debug("Deleted workspace memberships: workspaceId={}", workspaceId);

        // Step 4: Delete activity events (can be large - consider batching for very large workspaces)
        activityEventRepository.deleteAllByWorkspaceId(workspaceId);
        log.debug("Deleted activity events: workspaceId={}", workspaceId);

        // Step 5: Delete repository monitors
        // Note: The RepositoryToMonitor entities are also managed via Workspace.repositoriesToMonitor
        // with orphanRemoval=true, but we explicitly delete here for clarity and to ensure
        // the collection is cleared before workspace save
        repositoryToMonitorRepository.deleteAllByWorkspaceId(workspaceId);
        workspace.getRepositoriesToMonitor().clear();
        log.debug("Deleted repository monitors: workspaceId={}", workspaceId);

        // Step 6: Delete slug history (redirect entries)
        workspaceSlugHistoryRepository.deleteAllByWorkspaceId(workspaceId);
        log.debug("Deleted slug history: workspaceId={}", workspaceId);

        // Step 7: Unlink organization (don't delete - Organization is a shared entity)
        workspace.setOrganization(null);

        // Step 8: Mark workspace as PURGED (terminal state)
        workspace.setStatus(WorkspaceStatus.PURGED);
        workspace = workspaceRepository.save(workspace);

        log.info("Purged workspace: workspaceSlug={}, workspaceId={}", sanitizedSlug, workspaceId);
        return workspace;
    }

    @Transactional
    public Workspace purgeWorkspace(WorkspaceContext workspaceContext) {
        return purgeWorkspace(requireSlug(workspaceContext));
    }

    /**
     * Get the current status of a workspace.
     *
     * @param slug the workspace slug
     * @return the workspace status
     * @throws EntityNotFoundException if workspace does not exist
     */
    public WorkspaceStatus getWorkspaceStatus(String workspaceSlug) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));
        return workspace.getStatus();
    }

    public WorkspaceStatus getWorkspaceStatus(WorkspaceContext workspaceContext) {
        return getWorkspaceStatus(requireSlug(workspaceContext));
    }

    /**
     * Update the lifecycle status for the workspace using the canonical transition helpers.
     */
    @Transactional
    public Workspace updateStatus(String workspaceSlug, WorkspaceStatus targetStatus) {
        return switch (targetStatus) {
            case ACTIVE -> resumeWorkspace(workspaceSlug);
            case SUSPENDED -> suspendWorkspace(workspaceSlug);
            case PURGED -> purgeWorkspace(workspaceSlug);
        };
    }

    @Transactional
    public Workspace updateStatus(WorkspaceContext workspaceContext, WorkspaceStatus targetStatus) {
        return updateStatus(requireSlug(workspaceContext), targetStatus);
    }

    private String requireSlug(WorkspaceContext workspaceContext) {
        if (workspaceContext == null) {
            throw new EntityNotFoundException("Workspace", "context");
        }

        String slug = workspaceContext.slug();
        if (slug == null || slug.isBlank()) {
            throw new EntityNotFoundException("Workspace", "context");
        }

        return slug;
    }

    // -------------------------------------------------------------------------
    // NATS consumer lifecycle helpers
    // -------------------------------------------------------------------------

    /**
     * Stop NATS consumer for a workspace.
     * Only applies when NATS is enabled and workspace uses GitHub App mode.
     */
    private void stopNatsForWorkspace(Workspace workspace) {
        if (shouldUseNats(workspace)) {
            natsConsumerService.stopConsumingScope(workspace.getId());
        }
    }

    /**
     * Start NATS consumer for a workspace.
     * Only applies when NATS is enabled and workspace uses GitHub App mode.
     */
    private void startNatsForWorkspace(Workspace workspace) {
        if (shouldUseNats(workspace)) {
            natsConsumerService.startConsumingScope(workspace.getId());
        }
    }

    /**
     * Checks if NATS should be used for the given workspace.
     * NATS consumers are only used when:
     * <ul>
     *   <li>NATS is enabled globally</li>
     *   <li>Workspace exists</li>
     *   <li>Workspace has an installation ID (GitHub App mode)</li>
     * </ul>
     */
    private boolean shouldUseNats(Workspace workspace) {
        return isNatsEnabled && workspace != null && workspace.getInstallationId() != null;
    }
}
