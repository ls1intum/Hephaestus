package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Outline's {@link IntegrationSyncRunner}: the manual-trigger body behind {@code POST
 * /connections/{id}/sync/jobs} — exactly the full workspace reconcile
 * {@code OutlineConnectionAdminController}'s absorbed {@code POST /connections/outline/sync} used to run,
 * now dispatched by {@code SyncStatusService} through the shared job template instead of Outline's own
 * (now-removed) {@code syncsInFlight} guard.
 *
 * <p>Runs on the calling thread inside {@link de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService#run}
 * — job creation/guard/heartbeat already happened before this method is invoked, so it must NOT create
 * another job itself (that would immediately conflict with the one already RUNNING for this connection).
 */
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

    @Override
    public void reconcile(IntegrationRef ref, SyncJobHandle handle) {
        syncScheduler.syncWorkspaceNow(ref.workspaceId(), OutlineSyncProgress.adapt(handle));
        // The progress listener stops the reconcile between collections when the handle is cancelled, so
        // a still-set flag on return means it aborted early — declare it so the job finalizes CANCELLED.
        if (handle.isCancellationRequested()) {
            handle.reportCancelled();
        }
    }

    // supportsBackfill() stays the interface default (false) — Outline has no separate backfill phase;
    // every pass (scheduled, catch-up, or manual) is the same full reconcile.
}
