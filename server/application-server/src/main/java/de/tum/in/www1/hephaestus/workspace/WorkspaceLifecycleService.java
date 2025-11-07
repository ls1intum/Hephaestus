package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.workspace.exception.WorkspaceNotFoundException;
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
     * @throws WorkspaceNotFoundException if workspace does not exist
     * @throws IllegalStateException if workspace is already purged
     */
    @Transactional
    public Workspace suspendWorkspace(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new WorkspaceNotFoundException(slug));

        if (workspace.getStatus() == Workspace.WorkspaceStatus.PURGED) {
            throw new IllegalStateException("Cannot suspend a purged workspace: " + slug);
        }

        if (workspace.getStatus() != Workspace.WorkspaceStatus.SUSPENDED) {
            workspace.setStatus(Workspace.WorkspaceStatus.SUSPENDED);
            workspace = workspaceRepository.save(workspace);
            logger.info("Workspace '{}' has been suspended.", slug);

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
     * @throws WorkspaceNotFoundException if workspace does not exist
     * @throws IllegalStateException if workspace is purged (cannot resume purged)
     */
    @Transactional
    public Workspace resumeWorkspace(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new WorkspaceNotFoundException(slug));

        if (workspace.getStatus() == Workspace.WorkspaceStatus.PURGED) {
            throw new IllegalStateException("Cannot resume a purged workspace: " + slug);
        }

        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) {
            workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
            workspace = workspaceRepository.save(workspace);
            logger.info("Workspace '{}' has been resumed.", slug);

            // TODO: Restart NATS consumers and re-enable schedulers
        }

        return workspace;
    }

    /**
     * Purge (soft delete) a workspace immediately.
     * Idempotent: calling purge on an already purged workspace is a no-op.
     * 
     * @param slug the workspace slug
     * @throws WorkspaceNotFoundException if workspace does not exist
     */
    @Transactional
    public void purgeWorkspace(String slug) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new WorkspaceNotFoundException(slug));

        if (workspace.getStatus() == Workspace.WorkspaceStatus.PURGED) {
            logger.info("Workspace '{}' is already purged. Skipping.", slug);
            return;
        }

        // TODO: Implement hard delete with batch cascade strategy

        workspace.setStatus(Workspace.WorkspaceStatus.PURGED);
        workspaceRepository.save(workspace);
        logger.info("Workspace '{}' has been purged (soft deleted).", slug);
    }

    /**
     * Check if a workspace is active (not suspended or purged).
     * 
     * @param workspace the workspace to check
     * @return true if workspace is ACTIVE, false otherwise
     */
    public boolean isWorkspaceActive(Workspace workspace) {
        return workspace != null && workspace.getStatus() == Workspace.WorkspaceStatus.ACTIVE;
    }

    /**
     * Check if a workspace is suspended.
     * 
     * @param workspace the workspace to check
     * @return true if workspace is SUSPENDED, false otherwise
     */
    public boolean isWorkspaceSuspended(Workspace workspace) {
        return workspace != null && workspace.getStatus() == Workspace.WorkspaceStatus.SUSPENDED;
    }

    /**
     * Check if a workspace is purged (soft deleted).
     * 
     * @param workspace the workspace to check
     * @return true if workspace is PURGED, false otherwise
     */
    public boolean isWorkspacePurged(Workspace workspace) {
        return workspace != null && workspace.getStatus() == Workspace.WorkspaceStatus.PURGED;
    }
}
