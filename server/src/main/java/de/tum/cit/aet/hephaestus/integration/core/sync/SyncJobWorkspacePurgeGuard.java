package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.workspace.exception.WorkspaceLifecycleViolationException;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeGuard;
import org.springframework.stereotype.Component;

@Component
class SyncJobWorkspacePurgeGuard implements WorkspacePurgeGuard {

    private final SyncJobRepository syncJobRepository;

    SyncJobWorkspacePurgeGuard(SyncJobRepository syncJobRepository) {
        this.syncJobRepository = syncJobRepository;
    }

    @Override
    public void verifyQuiescent(Long workspaceId) {
        if (syncJobRepository.existsByWorkspace_IdAndStatusIn(workspaceId, SyncJobStatus.ACTIVE)) {
            throw new WorkspaceLifecycleViolationException(
                "This workspace has an active integration sync. Cancel it, wait for it to stop, then try again."
            );
        }
    }
}
