package de.tum.cit.aet.hephaestus.integration.outline.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.core.task.support.TaskExecutorAdapter;

/**
 * Deterministic unit tests for {@link OutlineCollectionAdminService} (all collaborators mocked; the
 * async kick runs synchronously through a same-thread executor). Each test pins one contract of the
 * admin control plane: live verification on register, idempotency on the natural key, the
 * resume-resets-PENDING rule, the delete-erases-documents rule, and the candidates' mirrored flag.
 */
class OutlineCollectionAdminServiceTest extends BaseUnitTest {

    private static final long WS = 7L;
    private static final long CONNECTION_ID = 42L;
    private static final String SERVER_URL = "https://outline.example.test";
    private static final String TOKEN = "outline-token";
    private static final String COLLECTION_ID = "col-1";

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineCollectionRepository collectionRepository;

    @Mock
    private OutlineDocumentRepository documentRepository;

    @Mock
    private OutlineApiClient outlineApiClient;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    @Mock
    private Connection connection;

    private OutlineCollectionAdminService service() {
        lenient().when(connection.getId()).thenReturn(CONNECTION_ID);
        lenient()
            .when(connection.getConfig())
            .thenReturn(new ConnectionConfig.OutlineConfig(SERVER_URL, null, null, Set.of()));
        lenient().when(connectionService.findActive(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection));
        lenient()
            .when(connectionService.findActiveBearerToken(WS, IntegrationKind.OUTLINE))
            .thenReturn(Optional.of(new BearerToken(TOKEN, null)));
        lenient()
            .when(collectionRepository.save(org.mockito.ArgumentMatchers.any(OutlineCollection.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return new OutlineCollectionAdminService(
            connectionService,
            collectionRepository,
            documentRepository,
            outlineApiClient,
            syncScheduler,
            new TaskExecutorAdapter(Runnable::run)
        );
    }

    private OutlineCollection registeredRow(MirrorState state, SyncStatus syncStatus) {
        OutlineCollection row = new OutlineCollection();
        row.setWorkspaceId(WS);
        row.setConnectionId(CONNECTION_ID);
        row.setCollectionId(COLLECTION_ID);
        row.setName("Design");
        row.setState(state);
        row.setSyncStatus(syncStatus);
        return row;
    }

    private void stubLiveCollections(OutlineCollectionListResponse.Collection... collections) {
        when(outlineApiClient.listCollections(SERVER_URL, TOKEN)).thenReturn(List.of(collections));
    }

    @Test
    void register_verifiesAgainstLiveList_capturesCatalogFields_andKicksTargetedSync() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.empty());
        stubLiveCollections(
            new OutlineCollectionListResponse.Collection(COLLECTION_ID, "Design", "col1", "#F00", "ruler")
        );

        OutlineCollectionAdminService.RegistrationOutcome outcome = service.register(WS, COLLECTION_ID);

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.collection().collectionId()).isEqualTo(COLLECTION_ID);
        assertThat(outcome.collection().name()).isEqualTo("Design");
        assertThat(outcome.collection().urlId()).isEqualTo("col1");
        assertThat(outcome.collection().state()).isEqualTo(MirrorState.ENABLED);
        assertThat(outcome.collection().syncStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(outcome.collection().documentCount()).isZero();

        ArgumentCaptor<OutlineCollection> saved = ArgumentCaptor.forClass(OutlineCollection.class);
        verify(collectionRepository).save(saved.capture());
        assertThat(saved.getValue().getWorkspaceId()).isEqualTo(WS);
        assertThat(saved.getValue().getConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(saved.getValue().getColor()).isEqualTo("#F00");
        assertThat(saved.getValue().getIcon()).isEqualTo("ruler");

        verify(syncScheduler).syncCollectionNow(WS, COLLECTION_ID);
    }

    @Test
    void register_existingRow_isIdempotent_noLiveCallNoSaveNoKick() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.of(registeredRow(MirrorState.ENABLED, SyncStatus.COMPLETE)));
        when(
            documentRepository.countByWorkspaceIdAndConnectionIdAndCollectionIdAndDeletedAtIsNull(
                WS,
                CONNECTION_ID,
                COLLECTION_ID
            )
        ).thenReturn(5L);

        OutlineCollectionAdminService.RegistrationOutcome outcome = service.register(WS, COLLECTION_ID);

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.collection().documentCount()).isEqualTo(5L);
        verify(outlineApiClient, never()).listCollections(anyString(), anyString());
        verify(collectionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(syncScheduler, never()).syncCollectionNow(eq(WS), anyString());
    }

    @Test
    void register_unknownCollectionId_throws422Shape_andCreatesNothing() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.empty());
        stubLiveCollections(new OutlineCollectionListResponse.Collection("other-id", "Other", null, null, null));

        assertThatThrownBy(() -> service.register(WS, COLLECTION_ID)).isInstanceOf(
            UnknownOutlineCollectionException.class
        );
        verify(collectionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(syncScheduler, never()).syncCollectionNow(eq(WS), anyString());
    }

    @Test
    void register_withoutActiveConnection_isNotFound() {
        OutlineCollectionAdminService service = service();
        when(connectionService.findActive(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(WS, COLLECTION_ID)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void pause_freezesWithoutKick_andKeepsSyncStatus() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.of(registeredRow(MirrorState.ENABLED, SyncStatus.COMPLETE)));

        OutlineCollectionDTO dto = service.updateState(WS, COLLECTION_ID, MirrorState.PAUSED);

        assertThat(dto.state()).isEqualTo(MirrorState.PAUSED);
        assertThat(dto.syncStatus()).isEqualTo(SyncStatus.COMPLETE);
        verify(syncScheduler, never()).syncCollectionNow(eq(WS), anyString());
    }

    @Test
    void resume_resetsSyncStatusToPending_andKicksTargetedSync() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.of(registeredRow(MirrorState.PAUSED, SyncStatus.COMPLETE)));

        OutlineCollectionDTO dto = service.updateState(WS, COLLECTION_ID, MirrorState.ENABLED);

        assertThat(dto.state()).isEqualTo(MirrorState.ENABLED);
        assertThat(dto.syncStatus()).isEqualTo(SyncStatus.PENDING);
        verify(syncScheduler).syncCollectionNow(WS, COLLECTION_ID);
    }

    @Test
    void updateState_sameState_isIdempotentNoSaveNoKick() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.of(registeredRow(MirrorState.PAUSED, SyncStatus.COMPLETE)));

        OutlineCollectionDTO dto = service.updateState(WS, COLLECTION_ID, MirrorState.PAUSED);

        assertThat(dto.state()).isEqualTo(MirrorState.PAUSED);
        verify(collectionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(syncScheduler, never()).syncCollectionNow(eq(WS), anyString());
    }

    @Test
    void updateState_unknownCollection_isNotFound() {
        OutlineCollectionAdminService service = service();
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateState(WS, COLLECTION_ID, MirrorState.PAUSED)).isInstanceOf(
            EntityNotFoundException.class
        );
    }

    @Test
    void delete_erasesTheCollectionsDocuments_andTheRegistryRow() {
        OutlineCollectionAdminService service = service();
        OutlineCollection row = registeredRow(MirrorState.ENABLED, SyncStatus.COMPLETE);
        when(
            collectionRepository.findByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID)
        ).thenReturn(Optional.of(row));

        service.delete(WS, COLLECTION_ID);

        verify(documentRepository).deleteByWorkspaceIdAndConnectionIdAndCollectionId(WS, CONNECTION_ID, COLLECTION_ID);
        verify(collectionRepository).delete(row);
    }

    @Test
    void candidates_flagAlreadyMirroredRows() {
        OutlineCollectionAdminService service = service();
        when(collectionRepository.findByWorkspaceIdOrderByCreatedAtAsc(WS)).thenReturn(
            List.of(registeredRow(MirrorState.ENABLED, SyncStatus.COMPLETE))
        );
        stubLiveCollections(
            new OutlineCollectionListResponse.Collection(COLLECTION_ID, "Design", "col1", null, null),
            new OutlineCollectionListResponse.Collection("col-2", "Archive", "col2", null, null)
        );

        List<OutlineCollectionCandidateDTO> candidates = service.listCandidates(WS);

        assertThat(candidates).hasSize(2);
        // Sorted by name: Archive before Design.
        assertThat(candidates.get(0).collectionId()).isEqualTo("col-2");
        assertThat(candidates.get(0).alreadyMirrored()).isFalse();
        assertThat(candidates.get(1).collectionId()).isEqualTo(COLLECTION_ID);
        assertThat(candidates.get(1).alreadyMirrored()).isTrue();
    }
}
