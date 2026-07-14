package de.tum.cit.aet.hephaestus.integration.outline.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduling fan-out for the Outline document sync.
 *
 * <p>Two loops: the six-hour full reconcile enumerates every workspace with an ACTIVE Outline
 * Connection, and the fast catch-up tick sweeps only workspaces with a collection still awaiting a
 * clean pass (normally none → zero API calls). Each workspace is delegated to
 * {@link OutlineDocumentSyncService} — a separate bean whose {@code REQUIRES_NEW} boundary only takes
 * effect across a real proxy hop, so one workspace's failure is isolated and the fan-out continues.
 * Webhook-subscription upkeep lives inside the reconcile itself (the registrar's self-heal), not here.
 *
 * <p>This bean owns only scheduling and cross-pod locking. Scheduling is gated to the server role, and
 * {@link SchedulerLock} stops concurrent pods from both running a loop.
 *
 * <p><b>Tenancy.</b> {@link WorkspaceAgnostic} is on the FAN-OUT methods only — they enumerate the fleet
 * ({@code findWorkspaceIdsWithActiveConnection}, {@code findDistinctWorkspaceIdsWithPendingSync}) before any
 * tenant is bound, so their SQL cannot carry a {@code workspace_id} predicate. It is deliberately NOT on the
 * type: a type-level bypass would also blanket the single-workspace {@code *Now} pass-throughs, disabling
 * {@code WorkspaceStatementInspector} on precisely the paths that take vendor-supplied ids straight off a
 * webhook. Those paths are already workspace-scoped (every query takes the {@code workspaceId}), so they keep
 * the safety net on. The {@code *Now} methods still exist as a bean hop for webhook consumers and async
 * listeners; they simply no longer buy a tenancy bypass.
 */
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@Component
public class OutlineDocumentSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutlineDocumentSyncScheduler.class);

    private final ConnectionService connectionService;
    private final OutlineDocumentSyncService syncService;
    private final OutlineCollectionRepository collectionRepository;

    public OutlineDocumentSyncScheduler(
        ConnectionService connectionService,
        OutlineDocumentSyncService syncService,
        OutlineCollectionRepository collectionRepository
    ) {
        this.connectionService = connectionService;
        this.syncService = syncService;
        this.collectionRepository = collectionRepository;
    }

    @Scheduled(cron = "${hephaestus.integration.outline.sync.cron}")
    @SchedulerLock(name = "outline-document-sync", lockAtMostFor = "PT1H", lockAtLeastFor = "PT30S")
    @WorkspaceAgnostic("Cron fan-out: enumerates every workspace with an ACTIVE Outline connection")
    public void syncAll() {
        syncAllNow();
    }

    /**
     * Catch-up tick: resume every collection still awaiting a clean pass (freshly registered, or a
     * reconcile ran out of export budget). Workspaces without pending collections cost nothing.
     */
    @Scheduled(fixedDelayString = "${hephaestus.integration.outline.sync.catch-up-delay}")
    @SchedulerLock(name = "outline-sync-catch-up", lockAtMostFor = "PT4M", lockAtLeastFor = "PT10S")
    @WorkspaceAgnostic("Catch-up fan-out: enumerates the workspaces with a collection still awaiting a clean pass")
    public void catchUp() {
        for (Long workspaceId : collectionRepository.findDistinctWorkspaceIdsWithPendingSync()) {
            try {
                syncService.syncPendingCollections(workspaceId);
            } catch (RuntimeException e) {
                // Isolate a poisoned workspace — log and keep catching up the rest.
                log.warn("outline.sync: catch-up failed for workspaceId={}: {}", workspaceId, e.toString());
            }
        }
    }

    /**
     * Run the full reconcile immediately across every workspace with an ACTIVE Outline Connection.
     * Exposed (not only cron-driven) so callers and integration tests can drive it deterministically.
     *
     * @return the number of workspaces the reconcile was attempted for
     */
    @WorkspaceAgnostic("Fleet fan-out: enumerates every workspace with an ACTIVE Outline connection")
    public int syncAllNow() {
        List<Long> workspaceIds = connectionService.findWorkspaceIdsWithActiveConnection(IntegrationKind.OUTLINE);
        for (Long workspaceId : workspaceIds) {
            try {
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
     * Full reconcile of a single workspace immediately — the entry point for webhook consumers and async
     * listeners, which reach the sync service through this bean. Explicitly workspace-SCOPED: it carries no
     * tenancy bypass, so {@code WorkspaceStatementInspector} stays armed for the whole call (see the class
     * javadoc — these are the paths fed by vendor-supplied ids).
     */
    public void syncWorkspaceNow(long workspaceId) {
        syncService.syncWorkspace(workspaceId);
    }

    /** Targeted single-collection sync; workspace-scoped, no bypass — see {@link #syncWorkspaceNow}. */
    public void syncCollectionNow(long workspaceId, String collectionId) {
        syncService.syncCollection(workspaceId, collectionId);
    }

    /** Webhook targeted document refresh; workspace-scoped, no bypass — see {@link #syncWorkspaceNow}. */
    public void refreshDocumentNow(long workspaceId, String eventName, String documentId) {
        syncService.refreshDocument(workspaceId, eventName, documentId);
    }

    /**
     * Webhook targeted document refresh carrying the delivery's pre-parsed {@code payload.model} (already
     * authenticated by the envelope's HMAC), letting the sync service skip its own {@code documents.info}
     * round-trip when the model is usable. Workspace-scoped, no bypass — see {@link #syncWorkspaceNow}.
     */
    public void refreshDocumentNow(
        long workspaceId,
        String eventName,
        String documentId,
        OutlineDocumentListResponse.@Nullable Meta prefetchedMeta
    ) {
        syncService.refreshDocument(workspaceId, eventName, documentId, prefetchedMeta);
    }

    /** Webhook collection-event catalog refresh; workspace-scoped, no bypass — see {@link #syncWorkspaceNow}. */
    public void refreshCollectionCatalogNow(long workspaceId, String eventName, @Nullable String collectionId) {
        syncService.refreshCollectionCatalog(workspaceId, eventName, collectionId);
    }
}
