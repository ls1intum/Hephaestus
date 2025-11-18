package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
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
     * @throws IllegalStateException if workspace is already purged
     */
    @Transactional
    public Workspace suspendWorkspace(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            throw new IllegalStateException("Cannot suspend a purged workspace: " + slug);
        }

        if (workspace.getStatus() != WorkspaceStatus.SUSPENDED) {
            workspace.setStatus(WorkspaceStatus.SUSPENDED);
            workspace = workspaceRepository.save(workspace);
            logger.info("Workspace '{}' has been suspended.", LoggingUtils.sanitizeForLog(slug));
            // TODO: Stop NATS consumers and signal schedulers
        }

        return workspace;
    }

    /**
     * Resume a suspended workspace, making it active again.
     * Idempotent: calling resume on an already active workspace is a no-op.
     *
     * @param slug the workspace slug
     * @return the resumed workspace
     * @throws EntityNotFoundException if workspace does not exist
     * @throws IllegalStateException if workspace is purged (cannot resume purged)
     */
    @Transactional
    public Workspace resumeWorkspace(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            throw new IllegalStateException("Cannot resume a purged workspace: " + slug);
        }

        if (workspace.getStatus() != WorkspaceStatus.ACTIVE) {
            workspace.setStatus(WorkspaceStatus.ACTIVE);
            workspace = workspaceRepository.save(workspace);
            logger.info("Workspace '{}' has been resumed.", LoggingUtils.sanitizeForLog(slug));
            // TODO: Restart NATS consumers and re-enable schedulers
        }

        return workspace;
    }

    /**
     * Purge (soft delete) a workspace immediately.
     * Idempotent: calling purge on an already purged workspace is a no-op.
     *
     * @param slug the workspace slug
     * @throws EntityNotFoundException if workspace does not exist
     */
    @Transactional
    public void purgeWorkspace(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        if (workspace.getStatus() == WorkspaceStatus.PURGED) {
            logger.info("Workspace '{}' is already purged. Skipping.", LoggingUtils.sanitizeForLog(slug));
            return;
        }

        // TODO: Implement hard delete with batch cascade strategy

        workspace.setStatus(WorkspaceStatus.PURGED);
        workspaceRepository.save(workspace);
        logger.info("Workspace '{}' has been purged (soft deleted).", LoggingUtils.sanitizeForLog(slug));
    }

    /**
     * Get the current status of a workspace.
     *
     * @param slug the workspace slug
     * @return the workspace status
     * @throws EntityNotFoundException if workspace does not exist
     */
    public WorkspaceStatus getWorkspaceStatus(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));
        return workspace.getStatus();
    }
}
