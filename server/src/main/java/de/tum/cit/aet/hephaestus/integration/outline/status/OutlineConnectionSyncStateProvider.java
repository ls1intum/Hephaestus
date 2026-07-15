package de.tum.cit.aet.hephaestus.integration.outline.status;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.framework.CronSchedules;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Read-only Outline sync state built from persisted connection, collection, and document data. */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineConnectionSyncStateProvider implements ConnectionSyncStateProvider {

    private final ConnectionRepository connectionRepository;
    private final OutlineCollectionRepository collectionRepository;
    private final OutlineDocumentRepository documentRepository;
    private final String syncCron;

    public OutlineConnectionSyncStateProvider(
        ConnectionRepository connectionRepository,
        OutlineCollectionRepository collectionRepository,
        OutlineDocumentRepository documentRepository,
        @Value("${hephaestus.integration.outline.sync.cron:0 0 */6 * * *}") String syncCron
    ) {
        this.connectionRepository = connectionRepository;
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.syncCron = syncCron;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public ConnectionSyncDetails describe(IntegrationRef ref, long connectionId) {
        Boolean webhookRegistered = connectionRepository
            .findById(connectionId)
            .map(Connection::getConfig)
            .filter(ConnectionConfig.OutlineConfig.class::isInstance)
            .map(ConnectionConfig.OutlineConfig.class::cast)
            .map(config -> config.webhookSubscriptionId() != null && !config.webhookSubscriptionId().isBlank())
            .orElse(null);

        return new ConnectionSyncDetails(webhookRegistered, CronSchedules.nextRun(syncCron), null, null, false, null);
    }

    @Override
    public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        Map<String, Long> itemCountByCollectionId = documentRepository
            .countLiveByCollection(workspaceId, connectionId)
            .stream()
            .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        return collectionRepository
            .findByWorkspaceIdAndConnectionId(workspaceId, connectionId)
            .stream()
            .map(collection -> toResourceState(collection, itemCountByCollectionId))
            .toList();
    }

    private SyncResourceState toResourceState(OutlineCollection collection, Map<String, Long> itemCountByCollectionId) {
        long itemCount = itemCountByCollectionId.getOrDefault(collection.getCollectionId(), 0L);
        String name =
            collection.getName() != null && !collection.getName().isBlank()
                ? collection.getName()
                : collection.getCollectionId();
        return new SyncResourceState(
            collection.getId(),
            collection.getCollectionId(),
            name,
            SyncResourceState.Type.COLLECTION,
            resourceState(collection),
            collection.getDocumentsSyncedAt(),
            itemCount,
            collection.getDocumentsUpstream() == null ? null : collection.getDocumentsUpstream().longValue(),
            collection.getLastSyncError(),
            null,
            null
        );
    }

    /** {@code PAUSED} takes priority — a frozen collection's stale sync status would otherwise be misleading. */
    private static String resourceState(OutlineCollection collection) {
        if (collection.getState() == MirrorState.PAUSED) {
            return "PAUSED";
        }
        return collection.getSyncStatus().name();
    }
}
