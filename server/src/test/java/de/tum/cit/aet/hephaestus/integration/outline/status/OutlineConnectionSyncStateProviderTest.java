package de.tum.cit.aet.hephaestus.integration.outline.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Mapping coverage for {@link OutlineConnectionSyncStateProvider} — the read model that replaced
 * {@code OutlineConnectionAdminService.status()} in the unified sync-observability API. Both methods must
 * stay O(DB + in-memory): nothing here talks to {@code OutlineApiClient}.
 */
class OutlineConnectionSyncStateProviderTest extends BaseUnitTest {

    private static final long WORKSPACE = 7L;
    private static final long CONNECTION_ID = 42L;
    private static final IntegrationRef REF = new IntegrationRef(IntegrationKind.OUTLINE, WORKSPACE, "team-1");

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private OutlineCollectionRepository collectionRepository;

    @Mock
    private OutlineDocumentRepository documentRepository;

    @Mock
    private Connection connection;

    private OutlineConnectionSyncStateProvider provider(String cron) {
        return new OutlineConnectionSyncStateProvider(
            connectionRepository,
            collectionRepository,
            documentRepository,
            cron
        );
    }

    private OutlineConnectionSyncStateProvider provider() {
        return provider("0 0 */6 * * *");
    }

    @Test
    void kind_isOutline() {
        assertThat(provider().kind()).isEqualTo(IntegrationKind.OUTLINE);
    }

    @Test
    void describe_webhookSubscriptionIdPresent_isRegistered() {
        lenient()
            .when(connection.getConfig())
            .thenReturn(
                new ConnectionConfig.OutlineConfig("https://outline.example.test", "sub-1", "secret", Set.of())
            );
        when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(connection));

        ConnectionSyncDetails details = provider().describe(REF, CONNECTION_ID);

        assertThat(details.webhookRegistered()).isTrue();
        // Outline is a full-reconcile model — no separate backfill or rate-limit tracking.
        assertThat(details.backfill()).isNull();
        assertThat(details.rateLimit()).isNull();
        assertThat(details.vendorHealthDegraded()).isFalse();
    }

    @Test
    void describe_blankWebhookSubscriptionId_isNotRegistered() {
        lenient()
            .when(connection.getConfig())
            .thenReturn(new ConnectionConfig.OutlineConfig("https://outline.example.test", " ", "secret", Set.of()));
        when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(connection));

        assertThat(provider().describe(REF, CONNECTION_ID).webhookRegistered()).isFalse();
    }

    @Test
    void describe_connectionMissing_webhookRegisteredIsNullNotFalse() {
        // Unknown, not "confirmed absent" — the connection row vanished between calls.
        when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.empty());

        assertThat(provider().describe(REF, CONNECTION_ID).webhookRegistered()).isNull();
    }

    @Test
    void describe_nextScheduledSyncAt_derivedFromTheCronExpression() {
        when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.empty());

        Instant next = provider("0 0 0 * * *").describe(REF, CONNECTION_ID).nextScheduledSyncAt();

        assertThat(next).isNotNull().isAfter(Instant.now());
    }

    @Test
    void describe_invalidCron_yieldsNullNextScheduledSyncAt() {
        when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.empty());

        assertThat(provider("not a cron").describe(REF, CONNECTION_ID).nextScheduledSyncAt()).isNull();
    }

    @Test
    void resources_mapsOneRowPerCollection() {
        OutlineCollection collection = new OutlineCollection();
        collection.setId(101L);
        collection.setWorkspaceId(WORKSPACE);
        collection.setConnectionId(CONNECTION_ID);
        collection.setCollectionId("col-1");
        collection.setName("Design");
        collection.setState(MirrorState.ENABLED);
        collection.setSyncStatus(SyncStatus.COMPLETE);
        Instant syncedAt = Instant.parse("2026-07-01T00:00:00Z");
        collection.setDocumentsSyncedAt(syncedAt);
        collection.setDocumentsUpstream(12);
        collection.setLastSyncError(null);

        when(collectionRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION_ID)).thenReturn(
            List.of(collection)
        );
        when(
            documentRepository.countByWorkspaceIdAndConnectionIdAndCollectionIdAndDeletedAtIsNull(
                WORKSPACE,
                CONNECTION_ID,
                "col-1"
            )
        ).thenReturn(9L);

        List<SyncResourceState> resources = provider().resources(REF, CONNECTION_ID);

        assertThat(resources).hasSize(1);
        SyncResourceState resource = resources.get(0);
        assertThat(resource.id()).isEqualTo(101L);
        assertThat(resource.externalId()).isEqualTo("col-1");
        assertThat(resource.name()).isEqualTo("Design");
        assertThat(resource.type()).isEqualTo(SyncResourceState.Type.COLLECTION);
        assertThat(resource.state()).isEqualTo("COMPLETE");
        assertThat(resource.lastSyncedAt()).isEqualTo(syncedAt);
        assertThat(resource.itemCount()).isEqualTo(9L);
        assertThat(resource.upstreamCount()).isEqualTo(12L);
        assertThat(resource.lastError()).isNull();
        // No backfill concept for Outline.
        assertThat(resource.backfillCompletedThrough()).isNull();
        assertThat(resource.backfillPercent()).isNull();
    }

    @Test
    void resources_pausedCollection_reportsPausedRegardlessOfSyncStatus() {
        OutlineCollection paused = new OutlineCollection();
        paused.setId(102L);
        paused.setWorkspaceId(WORKSPACE);
        paused.setConnectionId(CONNECTION_ID);
        paused.setCollectionId("col-2");
        paused.setName("Archive");
        paused.setState(MirrorState.PAUSED);
        paused.setSyncStatus(SyncStatus.PENDING);

        when(collectionRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION_ID)).thenReturn(
            List.of(paused)
        );

        assertThat(provider().resources(REF, CONNECTION_ID).get(0).state()).isEqualTo("PAUSED");
    }

    @Test
    void resources_missingName_fallsBackToTheCollectionId() {
        OutlineCollection unnamed = new OutlineCollection();
        unnamed.setId(103L);
        unnamed.setWorkspaceId(WORKSPACE);
        unnamed.setConnectionId(CONNECTION_ID);
        unnamed.setCollectionId("col-3");
        unnamed.setState(MirrorState.ENABLED);
        unnamed.setSyncStatus(SyncStatus.PENDING);

        when(collectionRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION_ID)).thenReturn(
            List.of(unnamed)
        );

        assertThat(provider().resources(REF, CONNECTION_ID).get(0).name()).isEqualTo("col-3");
    }

    @Test
    void resources_carriesTheLastSyncErrorThrough() {
        OutlineCollection errored = new OutlineCollection();
        errored.setId(104L);
        errored.setWorkspaceId(WORKSPACE);
        errored.setConnectionId(CONNECTION_ID);
        errored.setCollectionId("col-4");
        errored.setName("Broken");
        errored.setState(MirrorState.ENABLED);
        errored.setSyncStatus(SyncStatus.PENDING);
        errored.setLastSyncError("Outline /api/documents.export failed (HTTP 500)");

        when(collectionRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION_ID)).thenReturn(
            List.of(errored)
        );

        assertThat(provider().resources(REF, CONNECTION_ID).get(0).lastError()).isEqualTo(
            "Outline /api/documents.export failed (HTTP 500)"
        );
    }

    @Test
    void resources_noCollections_isEmpty() {
        when(collectionRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION_ID)).thenReturn(List.of());

        assertThat(provider().resources(REF, CONNECTION_ID)).isEmpty();
    }
}
