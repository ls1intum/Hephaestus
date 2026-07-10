package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Admin surface behind the Outline connection endpoints: the health snapshot and the manual
 * "sync now" trigger. Owns the repository reads (the controller stays thin per the
 * {@code controllersDoNotAccessRepositories} arch rule) and resolves the workspace's ACTIVE
 * Outline connection up front so both operations 404 cleanly when the integration is not connected.
 */
@Service
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineConnectionAdminService {

    private static final Logger log = LoggerFactory.getLogger(OutlineConnectionAdminService.class);

    private final ConnectionService connectionService;
    private final OutlineCollectionRepository collectionRepository;
    private final OutlineDocumentRepository documentRepository;
    private final OutlineDocumentSyncScheduler syncScheduler;
    private final AsyncTaskExecutor taskExecutor;

    /**
     * Per-workspace in-flight guard for the manual reconcile: a workspace id is present while its
     * manually triggered sync is running. A duplicate submit while running dispatches nothing — the
     * endpoint still answers 202 pointing at the same status monitor. Per-pod on purpose: the guard
     * absorbs the double-click, not cross-pod concurrency (the sync itself is idempotent and the
     * scheduler's {@code SchedulerLock} covers the cron path).
     */
    private final Set<Long> syncsInFlight = ConcurrentHashMap.newKeySet();

    public OutlineConnectionAdminService(
        ConnectionService connectionService,
        OutlineCollectionRepository collectionRepository,
        OutlineDocumentRepository documentRepository,
        OutlineDocumentSyncScheduler syncScheduler,
        @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor
    ) {
        this.connectionService = connectionService;
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.syncScheduler = syncScheduler;
        this.taskExecutor = taskExecutor;
    }

    /**
     * The connection health snapshot: webhook subscription presence (from the ACTIVE connection's
     * config), the freshest clean-pass timestamp across the install's collections, the live mirrored
     * document count, whether a manual reconcile is in flight, and the pending / errored collection
     * counts (the figures the 202 monitor loop watches converge).
     */
    public OutlineConnectionStatusDTO status(long workspaceId) {
        Connection connection = requireActiveConnection(workspaceId);
        ConnectionConfig.OutlineConfig config = (ConnectionConfig.OutlineConfig) connection.getConfig();
        boolean webhookRegistered = config.webhookSubscriptionId() != null && !config.webhookSubscriptionId().isBlank();
        List<OutlineCollection> collections = collectionRepository
            .findByWorkspaceIdOrderByCreatedAtAsc(workspaceId)
            .stream()
            .filter(c -> Objects.equals(c.getConnectionId(), connection.getId()))
            .toList();
        Instant lastSyncedAt = collections
            .stream()
            .map(OutlineCollection::getDocumentsSyncedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
        long pendingCollections = collections
            .stream()
            .filter(c -> c.getState() == MirrorState.ENABLED && c.getSyncStatus() == SyncStatus.PENDING)
            .count();
        long erroredCollections = collections
            .stream()
            .filter(c -> c.getLastSyncError() != null)
            .count();
        long documentCount = documentRepository.countByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        return new OutlineConnectionStatusDTO(
            webhookRegistered,
            lastSyncedAt,
            documentCount,
            isSyncRunning(workspaceId),
            pendingCollections,
            erroredCollections
        );
    }

    /** Whether a manually triggered full reconcile is currently running for this workspace (on this pod). */
    public boolean isSyncRunning(long workspaceId) {
        return syncsInFlight.contains(workspaceId);
    }

    /**
     * Fires the full workspace reconcile off the request thread (the endpoint answers 202
     * immediately). Guarded per workspace: while a manually triggered reconcile is still running, a
     * duplicate submit dispatches nothing — the caller gets the same 202 pointing at the same status
     * monitor. Routed through the {@link OutlineDocumentSyncScheduler} pass-through so the executor
     * thread (which carries no request tenancy scope) crosses the {@code @WorkspaceAgnostic} bypass hop.
     */
    public void syncNow(long workspaceId) {
        requireActiveConnection(workspaceId);
        if (!syncsInFlight.add(workspaceId)) {
            log.debug(
                "outline.admin: manual reconcile already running for workspaceId={} — duplicate submit absorbed",
                workspaceId
            );
            return;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    syncScheduler.syncWorkspaceNow(workspaceId);
                } catch (RuntimeException e) {
                    // Fire-and-forget: the 202 already went out; the 6h reconcile is the safety net.
                    log.warn(
                        "outline.admin: manual reconcile failed for workspaceId={}: {}",
                        workspaceId,
                        e.toString()
                    );
                } finally {
                    syncsInFlight.remove(workspaceId);
                }
            });
        } catch (RuntimeException e) {
            // The executor rejected the task — clear the guard so the next submit can dispatch again.
            syncsInFlight.remove(workspaceId);
            throw e;
        }
    }

    /** The workspace's ACTIVE Outline connection, or {@link EntityNotFoundException} (404) when not connected. */
    private Connection requireActiveConnection(long workspaceId) {
        Connection connection = connectionService
            .findActive(workspaceId, IntegrationKind.OUTLINE)
            .orElseThrow(() -> new EntityNotFoundException("Outline connection", Long.toString(workspaceId)));
        if (!(connection.getConfig() instanceof ConnectionConfig.OutlineConfig)) {
            throw new EntityNotFoundException("Outline connection", Long.toString(workspaceId));
        }
        return connection;
    }
}
