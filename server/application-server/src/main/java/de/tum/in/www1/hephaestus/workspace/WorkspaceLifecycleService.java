package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.exception.WorkspaceLifecycleViolationException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service coordinating workspace lifecycle state transitions and validation.
 * Manages suspend, resume, and purge operations with proper guardrails.
 */
@Service
public class WorkspaceLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceLifecycleService.class);

    @Autowired
    private WorkspaceRepository workspaceRepository;

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
            logger.info("Workspace '{}' has been suspended.", LoggingUtils.sanitizeForLog(workspaceSlug));
            // TODO: Stop NATS consumers and signal schedulers
        }

        return workspace;
    }

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
            logger.info("Workspace '{}' has been resumed.", LoggingUtils.sanitizeForLog(workspaceSlug));
            // TODO: Restart NATS consumers and re-enable schedulers
        }

        return workspace;
    }

    public Workspace resumeWorkspace(WorkspaceContext workspaceContext) {
        return resumeWorkspace(requireSlug(workspaceContext));
    }

    /**
     * Purge (soft delete) a workspace immediately.
     * Idempotent: calling purge on an already purged workspace is a no-op.
     *
     * @param slug the workspace slug
     * @throws EntityNotFoundException if workspace does not exist
     */
    @Transactional
    public Workspace purgeWorkspace(String workspaceSlug) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            logger.info("Workspace '{}' is already purged. Skipping.", LoggingUtils.sanitizeForLog(workspaceSlug));
            return workspace;
        }

        // TODO: Implement hard delete with batch cascade strategy

        workspace.setStatus(WorkspaceStatus.PURGED);
        workspace = workspaceRepository.save(workspace);
        logger.info("Workspace '{}' has been purged (soft deleted).", LoggingUtils.sanitizeForLog(workspaceSlug));
        return workspace;
    }

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
}
