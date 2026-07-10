package de.tum.cit.aet.hephaestus.integration.outline.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.OutlineProperties;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineRateLimitedException;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionDocumentsResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocument;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.lifecycle.OutlineWebhookRegistrar;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit coverage for the sync paths the real-Postgres integration test cannot cheaply pin: the export
 * budget (exhaustion keeps a collection PENDING with no watermark and no tombstones), the webhook
 * targeted-refresh routing (delete tombstones without an API call; an update outside the mirrored
 * collections is ignored; a vanished document tombstones), and the rate-limit abort.
 */
class OutlineDocumentSyncServiceTest extends BaseUnitTest {

    private static final long WORKSPACE = 42L;
    private static final long CONNECTION = 7L;
    private static final String SERVER_URL = "https://outline.example.test";
    private static final String COLLECTION_ID = "col-1";
    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineApiClient outlineApiClient;

    @Mock
    private OutlineDocumentRepository documentRepository;

    @Mock
    private OutlineCollectionRepository collectionRepository;

    @Mock
    private OutlineWebhookRegistrar webhookRegistrar;

    @Mock
    private Connection connection;

    private OutlineCollection collection;

    private OutlineDocumentSyncService service(int exportBudget) {
        return service(exportBudget, 200);
    }

    private OutlineDocumentSyncService service(int exportBudget, int cacheMaxSizeMb) {
        OutlineProperties properties = new OutlineProperties(
            new OutlineProperties.Sync("0 0 */6 * * *", exportBudget, Duration.ofMinutes(5)),
            new OutlineProperties.Cache(cacheMaxSizeMb),
            Duration.ofDays(30)
        );
        return new OutlineDocumentSyncService(
            connectionService,
            outlineApiClient,
            documentRepository,
            collectionRepository,
            webhookRegistrar,
            properties
        );
    }

    @BeforeEach
    void setUp() {
        lenient().when(connection.getId()).thenReturn(CONNECTION);
        lenient()
            .when(connection.getConfig())
            .thenReturn(new ConnectionConfig.OutlineConfig(SERVER_URL, "sub-1", "secret", java.util.Set.of()));
        lenient()
            .when(connectionService.findActive(WORKSPACE, IntegrationKind.OUTLINE))
            .thenReturn(Optional.of(connection));
        lenient()
            .when(connectionService.findActiveBearerToken(WORKSPACE, IntegrationKind.OUTLINE))
            .thenReturn(Optional.of(new BearerToken("token", null)));
        lenient()
            .when(documentRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION))
            .thenReturn(List.of());
        lenient().when(documentRepository.sumBodySizeByWorkspaceId(WORKSPACE)).thenReturn(0L);
        lenient()
            .when(documentRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient()
            .when(collectionRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        collection = new OutlineCollection();
        collection.setWorkspaceId(WORKSPACE);
        collection.setConnectionId(CONNECTION);
        collection.setCollectionId(COLLECTION_ID);
        collection.setState(MirrorState.ENABLED);
        collection.setSyncStatus(SyncStatus.PENDING);
        lenient()
            .when(collectionRepository.findForSync(WORKSPACE, CONNECTION, MirrorState.ENABLED))
            .thenReturn(List.of(collection));
        lenient()
            .when(collectionRepository.findByWorkspaceIdOrderByCreatedAtAsc(WORKSPACE))
            .thenReturn(List.of(collection));
        lenient()
            .when(outlineApiClient.listCollections(SERVER_URL, "token"))
            .thenReturn(
                List.of(new OutlineCollectionListResponse.Collection(COLLECTION_ID, "Design", "col1", null, null))
            );
        lenient()
            .when(outlineApiClient.listCollectionDocuments(SERVER_URL, "token", COLLECTION_ID))
            .thenReturn(List.of());
    }

    private static OutlineDocumentListResponse.Meta meta(String id, Instant updatedAt) {
        return new OutlineDocumentListResponse.Meta(id, id, T1, updatedAt, id, null, COLLECTION_ID, null, null, null);
    }

    private OutlineDocument mirrored(String documentId) {
        OutlineDocument doc = new OutlineDocument();
        doc.setWorkspaceId(WORKSPACE);
        doc.setConnectionId(CONNECTION);
        doc.setDocumentId(documentId);
        doc.setCollectionId(COLLECTION_ID);
        return doc;
    }

    @Test
    void budgetExhaustion_keepsCollectionPendingWithoutWatermarkOrTombstones() {
        // Two docs need an export but only one budget unit exists; a stale mirrored row would be
        // tombstone bait if the pass wrongly counted as clean.
        OutlineDocument staleRow = mirrored("doc-stale");
        when(documentRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION)).thenReturn(List.of(staleRow));
        when(outlineApiClient.listDocuments(SERVER_URL, "token", COLLECTION_ID)).thenReturn(
            List.of(meta("doc-1", T2), meta("doc-2", T1))
        );
        when(outlineApiClient.exportDocument(eq(SERVER_URL), eq("token"), anyString())).thenReturn("# body");

        service(1).syncWorkspace(WORKSPACE);

        assertThat(collection.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(collection.getDocumentsSyncedThrough()).isNull();
        assertThat(collection.getDocumentsSyncedAt()).isNull();
        assertThat(staleRow.isDeleted()).isFalse();
        // Coverage counters are written even on a budget-exhausted pass — the enumeration completed
        // (upstream = doc-1 + doc-2; the local-only stale row is not upstream).
        assertThat(collection.getDocumentsUpstream()).isEqualTo(2);
        assertThat(collection.getExportsSkippedForBudget()).isEqualTo(1);
        // Newest-first ordering means the single budget unit went to doc-1.
        verify(outlineApiClient).exportDocument(SERVER_URL, "token", "doc-1");
        verify(outlineApiClient, never()).exportDocument(SERVER_URL, "token", "doc-2");
    }

    @Test
    void cleanPass_advancesWatermarkAndTombstonesVanishedRows() {
        OutlineDocument staleRow = mirrored("doc-stale");
        staleRow.setBodyMarkdown("# old");
        staleRow.setCreatedBySubject("user-1");
        when(documentRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION)).thenReturn(List.of(staleRow));
        when(outlineApiClient.listDocuments(SERVER_URL, "token", COLLECTION_ID)).thenReturn(List.of(meta("doc-1", T2)));
        when(outlineApiClient.exportDocument(SERVER_URL, "token", "doc-1")).thenReturn("# body");

        service(10).syncWorkspace(WORKSPACE);

        assertThat(collection.getSyncStatus()).isEqualTo(SyncStatus.COMPLETE);
        assertThat(collection.getDocumentsSyncedThrough()).isEqualTo(T2);
        assertThat(collection.getDocumentsSyncedAt()).isNotNull();
        // Clean-pass counters: full coverage, nothing skipped.
        assertThat(collection.getDocumentsUpstream()).isEqualTo(1);
        assertThat(collection.getExportsSkippedForBudget()).isZero();
        assertThat(staleRow.isDeleted()).isTrue();
        assertThat(staleRow.getBodyMarkdown()).isNull();
        assertThat(staleRow.getCreatedBySubject()).isNull();
    }

    @Test
    void rateLimit_abortsThePassWithoutMarkingComplete() {
        when(outlineApiClient.listDocuments(SERVER_URL, "token", COLLECTION_ID)).thenThrow(
            new OutlineRateLimitedException(Duration.ofSeconds(30), null)
        );

        service(10).syncWorkspace(WORKSPACE);

        assertThat(collection.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(collection.getDocumentsSyncedAt()).isNull();
    }

    @Test
    void refreshDocument_deleteEvent_tombstonesWithoutAnyApiCall() {
        OutlineDocument row = mirrored("doc-1");
        row.setBodyMarkdown("# body");
        when(
            documentRepository.findByWorkspaceIdAndConnectionIdAndDocumentId(WORKSPACE, CONNECTION, "doc-1")
        ).thenReturn(Optional.of(row));

        service(10).refreshDocument(WORKSPACE, "documents.delete", "doc-1");

        assertThat(row.isDeleted()).isTrue();
        assertThat(row.getBodyMarkdown()).isNull();
        verify(outlineApiClient, never()).getDocumentInfo(anyString(), anyString(), anyString());
        verify(outlineApiClient, never()).exportDocument(anyString(), anyString(), anyString());
    }

    @Test
    void refreshDocument_updateOutsideMirroredCollections_isIgnored() {
        when(
            documentRepository.findByWorkspaceIdAndConnectionIdAndDocumentId(WORKSPACE, CONNECTION, "doc-x")
        ).thenReturn(Optional.empty());
        when(outlineApiClient.getDocumentInfo(SERVER_URL, "token", "doc-x")).thenReturn(
            Optional.of(
                new OutlineDocumentListResponse.Meta("doc-x", "X", T1, T1, "doc-x", null, "other-col", null, null, null)
            )
        );
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WORKSPACE, CONNECTION, "other-col")
        ).thenReturn(Optional.empty());

        service(10).refreshDocument(WORKSPACE, "documents.update", "doc-x");

        verify(outlineApiClient, never()).exportDocument(anyString(), anyString(), anyString());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void refreshDocument_updateInMirroredCollection_exportsAndUpserts() {
        when(
            documentRepository.findByWorkspaceIdAndConnectionIdAndDocumentId(WORKSPACE, CONNECTION, "doc-1")
        ).thenReturn(Optional.empty());
        when(outlineApiClient.getDocumentInfo(SERVER_URL, "token", "doc-1")).thenReturn(Optional.of(meta("doc-1", T2)));
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WORKSPACE, CONNECTION, COLLECTION_ID)
        ).thenReturn(Optional.of(collection));
        when(outlineApiClient.exportDocument(SERVER_URL, "token", "doc-1")).thenReturn("# fresh");

        service(10).refreshDocument(WORKSPACE, "documents.update", "doc-1");

        verify(documentRepository).save(
            org.mockito.ArgumentMatchers.argThat(
                d -> "doc-1".equals(d.getDocumentId()) && "# fresh".equals(d.getBodyMarkdown())
            )
        );
    }

    @Test
    void refreshDocument_vanishedUpstream_tombstonesTheMirroredRow() {
        OutlineDocument row = mirrored("doc-1");
        row.setBodyMarkdown("# body");
        when(
            documentRepository.findByWorkspaceIdAndConnectionIdAndDocumentId(WORKSPACE, CONNECTION, "doc-1")
        ).thenReturn(Optional.of(row));
        when(outlineApiClient.getDocumentInfo(SERVER_URL, "token", "doc-1")).thenReturn(Optional.empty());

        service(10).refreshDocument(WORKSPACE, "documents.update", "doc-1");

        assertThat(row.isDeleted()).isTrue();
        assertThat(row.getBodyMarkdown()).isNull();
    }

    @Test
    void refreshCollectionCatalog_deleteEvent_tombstonesTheCollectionsDocuments() {
        OutlineDocument row = mirrored("doc-1");
        row.setBodyMarkdown("# body");
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WORKSPACE, CONNECTION, COLLECTION_ID)
        ).thenReturn(Optional.of(collection));
        when(
            documentRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WORKSPACE, CONNECTION, COLLECTION_ID)
        ).thenReturn(List.of(row));

        service(10).refreshCollectionCatalog(WORKSPACE, "collections.delete", COLLECTION_ID);

        assertThat(row.isDeleted()).isTrue();
        assertThat(collection.getLastSyncError()).contains("deleted in Outline");
    }

    @Test
    void export_capturesTimestampsAndCollaborators_andClearsTheEvictionMarker() {
        OutlineDocument evicted = mirrored("doc-1");
        evicted.setContentHash("stale-hash"); // an evicted row keeps its hash…
        evicted.setOutlineUpdatedAt(T1);
        evicted.setBodyEvictedAt(T1); // …and carries the eviction marker
        when(documentRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION)).thenReturn(List.of(evicted));
        when(outlineApiClient.listDocuments(SERVER_URL, "token", COLLECTION_ID)).thenReturn(
            List.of(
                new OutlineDocumentListResponse.Meta(
                    "doc-1",
                    "Doc 1",
                    T1,
                    T2, // upstream changed → re-export
                    "doc-1",
                    null,
                    COLLECTION_ID,
                    new OutlineDocumentListResponse.OutlineUser("user-1", "Ada"),
                    new OutlineDocumentListResponse.OutlineUser("user-2", "Grace"),
                    List.of("user-1", "user-2", "user-3")
                )
            )
        );
        when(outlineApiClient.exportDocument(SERVER_URL, "token", "doc-1")).thenReturn("# fresh");

        service(10).syncWorkspace(WORKSPACE);

        assertThat(evicted.getBodyMarkdown()).isEqualTo("# fresh");
        assertThat(evicted.getBodyEvictedAt()).isNull(); // the body is back — the marker must go
        assertThat(evicted.getOutlineCreatedAt()).isEqualTo(T1);
        assertThat(evicted.getOutlineUpdatedAt()).isEqualTo(T2);
        assertThat(evicted.getCollaboratorSubjects()).containsExactly("user-1", "user-2", "user-3");
    }

    @Test
    void evictedButUnmodifiedDocument_isNotReExported_theRetainedHashHoldsTheCap() {
        OutlineDocument evicted = mirrored("doc-1");
        evicted.setContentHash("kept-hash");
        evicted.setOutlineUpdatedAt(T1);
        evicted.setBodyEvictedAt(T1);
        when(documentRepository.findByWorkspaceIdAndConnectionId(WORKSPACE, CONNECTION)).thenReturn(List.of(evicted));
        when(outlineApiClient.listDocuments(SERVER_URL, "token", COLLECTION_ID)).thenReturn(
            List.of(meta("doc-1", T1)) // upstream unchanged
        );

        service(10).syncWorkspace(WORKSPACE);

        // No thrash loop: the unchanged fast path holds because eviction kept the hash.
        verify(outlineApiClient, never()).exportDocument(anyString(), anyString(), anyString());
        assertThat(evicted.getBodyEvictedAt()).isEqualTo(T1);
    }

    @Test
    void tombstone_clearsCollaboratorSubjectsAlongsideAuthors() {
        OutlineDocument row = mirrored("doc-1");
        row.setBodyMarkdown("# body");
        row.setContentHash("hash");
        row.setCollaboratorSubjects(List.of("user-1", "user-3"));
        when(
            documentRepository.findByWorkspaceIdAndConnectionIdAndDocumentId(WORKSPACE, CONNECTION, "doc-1")
        ).thenReturn(Optional.of(row));

        service(10).refreshDocument(WORKSPACE, "documents.delete", "doc-1");

        assertThat(row.isDeleted()).isTrue();
        assertThat(row.getBodyMarkdown()).isNull();
        assertThat(row.getContentHash()).isNull();
        assertThat(row.getCollaboratorSubjects()).isNull();
    }

    @Test
    void syncPendingCollections_enforcesTheSizeCapAfterThePass() {
        when(
            collectionRepository.findByWorkspaceIdAndStateAndSyncStatus(
                WORKSPACE,
                MirrorState.ENABLED,
                SyncStatus.PENDING
            )
        ).thenReturn(List.of(collection));
        when(outlineApiClient.listDocuments(SERVER_URL, "token", COLLECTION_ID)).thenReturn(List.of(meta("doc-1", T2)));
        when(outlineApiClient.exportDocument(SERVER_URL, "token", "doc-1")).thenReturn("# body");
        // Cap of 0 MB with a non-empty mirror → the eviction path must run.
        when(documentRepository.sumBodySizeByWorkspaceId(WORKSPACE)).thenReturn(1_000L);
        when(documentRepository.findEvictionCandidates(WORKSPACE)).thenReturn(
            List.of(new Object[][] { { 11L, 1_000L } })
        );

        service(10, 0).syncPendingCollections(WORKSPACE);

        verify(documentRepository).evictBodies(WORKSPACE, List.of(11L));
    }

    @Test
    void syncPendingCollections_makesNoApiCallsWhenNothingIsPending() {
        when(
            collectionRepository.findByWorkspaceIdAndStateAndSyncStatus(
                WORKSPACE,
                MirrorState.ENABLED,
                SyncStatus.PENDING
            )
        ).thenReturn(List.of());

        service(10).syncPendingCollections(WORKSPACE);

        verify(outlineApiClient, never()).listCollections(anyString(), anyString());
        verify(outlineApiClient, never()).listDocuments(anyString(), anyString(), anyString());
    }
}
