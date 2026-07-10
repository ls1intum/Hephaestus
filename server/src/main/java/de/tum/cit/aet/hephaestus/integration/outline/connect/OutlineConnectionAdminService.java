package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
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
     * config), the freshest clean-pass timestamp across the install's collections, and the live
     * mirrored document count.
     */
    public OutlineConnectionStatusDTO status(long workspaceId) {
        Connection connection = requireActiveConnection(workspaceId);
        ConnectionConfig.OutlineConfig config = (ConnectionConfig.OutlineConfig) connection.getConfig();
        boolean webhookRegistered = config.webhookSubscriptionId() != null && !config.webhookSubscriptionId().isBlank();
        Instant lastSyncedAt = collectionRepository
            .findByWorkspaceIdOrderByCreatedAtAsc(workspaceId)
            .stream()
            .filter(c -> Objects.equals(c.getConnectionId(), connection.getId()))
            .map(OutlineCollection::getDocumentsSyncedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
        long documentCount = documentRepository.countByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        return new OutlineConnectionStatusDTO(webhookRegistered, lastSyncedAt, documentCount);
    }

    /**
     * Fires the full workspace reconcile off the request thread (the endpoint answers 202
     * immediately). Routed through the {@link OutlineDocumentSyncScheduler} pass-through so the
     * executor thread (which carries no request tenancy scope) crosses the
     * {@code @WorkspaceAgnostic} bypass hop.
     */
    public void syncNow(long workspaceId) {
        requireActiveConnection(workspaceId);
        taskExecutor.execute(() -> {
            try {
                syncScheduler.syncWorkspaceNow(workspaceId);
            } catch (RuntimeException e) {
                // Fire-and-forget: the 202 already went out; the 6h reconcile is the safety net.
                log.warn("outline.admin: manual reconcile failed for workspaceId={}: {}", workspaceId, e.toString());
            }
        });
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
