package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncExecutionHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs a full Outline reconcile through the shared sync-job template. */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineIntegrationSyncRunner implements IntegrationSyncRunner {

    private final OutlineDocumentSyncScheduler syncScheduler;

    public OutlineIntegrationSyncRunner(OutlineDocumentSyncScheduler syncScheduler) {
        this.syncScheduler = syncScheduler;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    /** Outline has no deletion sweep of its own — {@code type} is unused here. */
    @Override
    public void reconcile(IntegrationRef ref, SyncExecutionHandle handle, SyncJobType type) {
        syncScheduler.syncWorkspaceNow(ref.workspaceId(), handle);
        if (handle.isCancellationRequested()) {
            handle.reportCancelled();
        }
    }
}
