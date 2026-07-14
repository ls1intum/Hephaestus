package de.tum.cit.aet.hephaestus.integration.outline.status;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Outline's {@link ConnectionSyncStateProvider}: read-only, O(DB) only — no vendor API call (see the
 * interface's class-level requirement). {@code describe()}/{@code resources()} reuse exactly the persisted
 * signals the old {@code OutlineConnectionAdminService.status()} snapshot read, now surfaced through the
 * unified read model instead of Outline's own absorbed {@code GET /connections/outline/status}.
 *
 * <p><b>webhookRegistered</b> is derived from the stored {@link ConnectionConfig.OutlineConfig#webhookSubscriptionId()}
 * — existence-only, matching the absorbed endpoint's semantics: Outline auto-disables a subscription after
 * repeated delivery failures, and a stale id here self-heals on the next reconcile rather than being probed live.
 *
 * <p><b>rateLimit</b> is always {@code null} — Outline has no tracked rate-limit budget (see design doc §3.2).
 *
 * <p><b>backfill</b> is always {@code null} — Outline has no separate backfill phase; every pass is a full
 * reconcile against the currently-registered collections (see {@link #resources} javadoc and
 * {@code IntegrationSyncRunner#supportsBackfill}).
 */
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

        return new ConnectionSyncDetails(webhookRegistered, nextScheduledSyncAt(), null, null, false, null);
    }

    /** Outline's "backfill" is a full reconcile every pass — nothing separate to compute a horizon from. */
    @Nullable
    private Instant nextScheduledSyncAt() {
        if (!CronExpression.isValidExpression(syncCron)) {
            return null;
        }
        return CronExpression.parse(syncCron).next(ZonedDateTime.now()).toInstant();
    }

    @Override
    public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
        return collectionRepository
            .findByWorkspaceIdAndConnectionId(ref.workspaceId(), connectionId)
            .stream()
            .map(collection -> toResourceState(ref.workspaceId(), collection))
            .toList();
    }

    private SyncResourceState toResourceState(long workspaceId, OutlineCollection collection) {
        long itemCount = documentRepository.countByWorkspaceIdAndConnectionIdAndCollectionIdAndDeletedAtIsNull(
            workspaceId,
            collection.getConnectionId(),
            collection.getCollectionId()
        );
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
            // No backfill concept for Outline — every pass is a full reconcile (BackfillSummary javadoc).
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
