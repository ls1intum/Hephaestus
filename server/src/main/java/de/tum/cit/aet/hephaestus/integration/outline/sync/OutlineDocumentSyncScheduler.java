package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.lifecycle.OutlineWebhookRegistrar;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic fan-out for the Outline document sync.
 *
 * <p>Enumerates every workspace with an ACTIVE Outline Connection and delegates each to
 * {@link OutlineDocumentSyncService} — a separate bean whose {@code REQUIRES_NEW} boundary only takes
 * effect across a real proxy hop, so one workspace's failure is isolated and the fan-out continues.
 *
 * <p>This bean owns only scheduling and cross-pod locking. It is {@link WorkspaceAgnostic} because the
 * reconcile is inherently cross-workspace. Scheduling is gated to the server role, and
 * {@link SchedulerLock} stops concurrent pods from both running it (same pattern as
 * {@code SlackRetentionSweeper}).
 */
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@Component
@WorkspaceAgnostic("Reconciling Outline documents across every workspace with an active connection")
public class OutlineDocumentSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutlineDocumentSyncScheduler.class);

    private final ConnectionService connectionService;
    private final OutlineDocumentSyncService syncService;
    private final OutlineWebhookRegistrar webhookRegistrar;

    public OutlineDocumentSyncScheduler(
        ConnectionService connectionService,
        OutlineDocumentSyncService syncService,
        OutlineWebhookRegistrar webhookRegistrar
    ) {
        this.connectionService = connectionService;
        this.syncService = syncService;
        this.webhookRegistrar = webhookRegistrar;
    }

    @Scheduled(cron = "${hephaestus.integration.outline.sync.cron}")
    @SchedulerLock(name = "outline-document-sync", lockAtMostFor = "PT1H", lockAtLeastFor = "PT30S")
    public void syncAll() {
        syncAllNow();
    }

    /**
     * Run the reconcile immediately across every workspace with an ACTIVE Outline Connection. Exposed (not
     * only cron-driven) so callers and integration tests can drive it deterministically.
     *
     * @return the number of workspaces the reconcile was attempted for
     */
    public int syncAllNow() {
        List<Long> workspaceIds = connectionService.findWorkspaceIdsWithActiveConnection(IntegrationKind.OUTLINE);
        for (Long workspaceId : workspaceIds) {
            try {
                // Register the change-notification subscription once (a no-op after the first cycle) so live
                // edits refresh the mirror between the periodic reconciles.
                webhookRegistrar.registerIfNeeded(workspaceId);
                syncService.syncWorkspace(workspaceId);
            } catch (RuntimeException e) {
                // Isolate a poisoned workspace — log and keep reconciling the rest.
                log.warn("outline.sync: reconcile failed for workspaceId={}: {}", workspaceId, e.toString());
            }
        }
        if (!workspaceIds.isEmpty()) {
            log.info("outline.sync: reconciled {} workspace(s)", workspaceIds.size());
        }
        return workspaceIds.size();
    }

    /**
     * Reconcile a single workspace immediately. Used by the change-notification path to refresh the mirror
     * of the workspace an authenticated Outline event names, on top of the periodic reconcile. Runs through
     * this {@link WorkspaceAgnostic} bean so the cross-workspace tenancy bypass is opened on the calling
     * thread (a webhook offloads this onto a worker thread that does not inherit the request's bypass scope).
     */
    public void syncWorkspaceNow(long workspaceId) {
        syncService.syncWorkspace(workspaceId);
    }
}
