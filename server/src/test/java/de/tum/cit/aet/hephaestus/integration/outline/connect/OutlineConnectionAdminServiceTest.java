package de.tum.cit.aet.hephaestus.integration.outline.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient.OutlineTokenDescription;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiException;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.SyncStatus;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.task.support.TaskExecutorAdapter;

/**
 * Deterministic unit tests for {@link OutlineConnectionAdminService}. The async dispatch runs through
 * a controllable executor so the per-workspace in-flight guard is observable at both boundaries: a
 * duplicate submit while a manual reconcile is running dispatches nothing (same 202, same monitor),
 * and a finished (or failed) run clears the flag so the next submit dispatches again. The status
 * snapshot's derived figures (pending / errored collection counts, syncRunning) are pinned against
 * seeded registry rows, including rows of a foreign connection that must not leak into the counts.
 */
class OutlineConnectionAdminServiceTest extends BaseUnitTest {

    private static final long WS = 7L;
    private static final long CONNECTION_ID = 42L;
    private static final String SERVER_URL = "https://outline.example.test";

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineApiClient apiClient;

    @Mock
    private OutlineCollectionRepository collectionRepository;

    @Mock
    private OutlineDocumentRepository documentRepository;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    @Mock
    private Connection connection;

    /** Runnables handed to the executor, collected instead of run — the test decides when a sync "finishes". */
    private final List<Runnable> dispatched = new ArrayList<>();

    private OutlineConnectionAdminService deferredService() {
        return service(runnable -> dispatched.add(runnable));
    }

    private OutlineConnectionAdminService sameThreadService() {
        return service(Runnable::run);
    }

    private OutlineConnectionAdminService service(java.util.concurrent.Executor executor) {
        lenient().when(connection.getId()).thenReturn(CONNECTION_ID);
        lenient()
            .when(connection.getConfig())
            .thenReturn(new ConnectionConfig.OutlineConfig(SERVER_URL, null, null, Set.of()));
        lenient().when(connectionService.findActive(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection));
        return new OutlineConnectionAdminService(
            connectionService,
            apiClient,
            collectionRepository,
            documentRepository,
            syncScheduler,
            new TaskExecutorAdapter(executor)
        );
    }

    /** The stored token, as the connection's credential bundle hands it to the probe. */
    private void storedToken(String token) {
        when(connectionService.findActiveBearerToken(WS, IntegrationKind.OUTLINE)).thenReturn(
            Optional.of(new BearerToken(token, null))
        );
    }

    private OutlineCollection row(
        long connectionId,
        MirrorState state,
        SyncStatus syncStatus,
        String lastSyncError,
        Instant syncedAt
    ) {
        OutlineCollection row = new OutlineCollection();
        row.setWorkspaceId(WS);
        row.setConnectionId(connectionId);
        row.setCollectionId("col-" + connectionId + "-" + state + "-" + syncStatus);
        row.setState(state);
        row.setSyncStatus(syncStatus);
        row.setLastSyncError(lastSyncError);
        row.setDocumentsSyncedAt(syncedAt);
        return row;
    }

    @Test
    void syncNow_duplicateSubmitWhileRunning_dispatchesNoSecondSync() {
        OutlineConnectionAdminService service = deferredService();

        service.syncNow(WS);
        service.syncNow(WS);

        // One runnable queued, and the sync itself has not been re-dispatched behind the guard.
        assertThat(dispatched).hasSize(1);
        dispatched.get(0).run();
        verify(syncScheduler, times(1)).syncWorkspaceNow(WS);
    }

    @Test
    void syncNow_guardIsVisibleWhileRunning_andClearsOnCompletion() {
        OutlineConnectionAdminService service = deferredService();

        assertThat(service.isSyncRunning(WS)).isFalse();
        service.syncNow(WS);
        assertThat(service.isSyncRunning(WS)).isTrue();

        dispatched.get(0).run();

        assertThat(service.isSyncRunning(WS)).isFalse();
        // With the guard cleared, the next submit dispatches again.
        service.syncNow(WS);
        assertThat(dispatched).hasSize(2);
    }

    @Test
    void syncNow_guardClearsEvenWhenTheSyncFails() {
        OutlineConnectionAdminService service = sameThreadService();
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(syncScheduler).syncWorkspaceNow(WS);

        // Fire-and-forget: the failure is swallowed (the 202 already went out) and the guard clears.
        service.syncNow(WS);

        assertThat(service.isSyncRunning(WS)).isFalse();
    }

    @Test
    void syncNow_withoutActiveConnection_isNotFound_andDispatchesNothing() {
        OutlineConnectionAdminService service = deferredService();
        when(connectionService.findActive(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncNow(WS)).isInstanceOf(EntityNotFoundException.class);
        assertThat(dispatched).isEmpty();
        assertThat(service.isSyncRunning(WS)).isFalse();
    }

    @Test
    void status_derivesPendingAndErroredCounts_fromThisConnectionsRowsOnly() {
        OutlineConnectionAdminService service = deferredService();
        Instant syncedAt = Instant.parse("2026-07-01T00:00:00Z");
        when(collectionRepository.findByWorkspaceIdOrderByCreatedAtAsc(WS)).thenReturn(
            List.of(
                row(CONNECTION_ID, MirrorState.ENABLED, SyncStatus.PENDING, null, null),
                row(CONNECTION_ID, MirrorState.ENABLED, SyncStatus.COMPLETE, "HTTP 500", syncedAt),
                // PAUSED + PENDING does not count as pending — a frozen collection is not awaited.
                row(CONNECTION_ID, MirrorState.PAUSED, SyncStatus.PENDING, null, null),
                // A foreign connection's row must not leak into any figure.
                row(CONNECTION_ID + 1, MirrorState.ENABLED, SyncStatus.PENDING, "other", null)
            )
        );
        when(documentRepository.countByWorkspaceIdAndDeletedAtIsNull(WS)).thenReturn(3L);

        OutlineConnectionStatusDTO status = service.status(WS);

        assertThat(status.pendingCollections()).isEqualTo(1L);
        assertThat(status.erroredCollections()).isEqualTo(1L);
        assertThat(status.documentCount()).isEqualTo(3L);
        assertThat(status.lastSyncedAt()).isEqualTo(syncedAt);
        assertThat(status.syncRunning()).isFalse();
        assertThat(status.webhookRegistered()).isFalse();
    }

    @Test
    void status_reportsSyncRunning_whileAManualReconcileIsInFlight() {
        OutlineConnectionAdminService service = deferredService();
        when(collectionRepository.findByWorkspaceIdOrderByCreatedAtAsc(WS)).thenReturn(List.of());
        when(documentRepository.countByWorkspaceIdAndDeletedAtIsNull(WS)).thenReturn(0L);

        service.syncNow(WS);
        assertThat(service.status(WS).syncRunning()).isTrue();

        dispatched.get(0).run();
        assertThat(service.status(WS).syncRunning()).isFalse();
    }

    @Test
    void tokenStatus_rejectedToken_isNotAccepted_ratherThanAnError() {
        // "Your token no longer works" is the answer the admin card came to ask — not a 5xx.
        OutlineConnectionAdminService service = deferredService();
        storedToken("ol_dead_key");
        org.mockito.Mockito.doThrow(new OutlineApiException("Outline /api/auth.info failed (HTTP 401)"))
            .when(apiClient)
            .validateToken(SERVER_URL, "ol_dead_key");

        OutlineTokenStatusDTO status = service.tokenStatus(WS);

        assertThat(status.accepted()).isFalse();
        assertThat(status.name()).isNull();
        assertThat(status.expiresAt()).isNull();
        // A dead token is never described — the metadata probe is not even attempted.
        verify(apiClient, never()).describeToken(any(), any());
    }

    @Test
    void tokenStatus_acceptedAndDescribable_carriesTheKeysMetadata() {
        OutlineConnectionAdminService service = deferredService();
        storedToken("ol_live_key");
        Instant expiresAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant lastActiveAt = Instant.parse("2026-07-13T08:30:00Z");
        when(apiClient.describeToken(SERVER_URL, "ol_live_key")).thenReturn(
            Optional.of(new OutlineTokenDescription("Hephaestus", "ab12", expiresAt, lastActiveAt))
        );

        OutlineTokenStatusDTO status = service.tokenStatus(WS);

        assertThat(status.accepted()).isTrue();
        assertThat(status.name()).isEqualTo("Hephaestus");
        assertThat(status.last4()).isEqualTo("ab12");
        assertThat(status.expiresAt()).isEqualTo(expiresAt);
        assertThat(status.lastActiveAt()).isEqualTo(lastActiveAt);
    }

    @Test
    void tokenStatus_acceptedButUndescribable_isAcceptedWithoutMetadata() {
        // apiKeys.list answered 403 (an out-of-scope key, or an owner who cannot see it) — the token
        // still syncs content, so the absence of metadata must not read as "token broken".
        OutlineConnectionAdminService service = deferredService();
        storedToken("ol_scoped_key");
        when(apiClient.describeToken(SERVER_URL, "ol_scoped_key")).thenReturn(Optional.empty());

        OutlineTokenStatusDTO status = service.tokenStatus(WS);

        assertThat(status.accepted()).isTrue();
        assertThat(status.name()).isNull();
        assertThat(status.last4()).isNull();
        assertThat(status.expiresAt()).isNull();
        assertThat(status.lastActiveAt()).isNull();
    }

    @Test
    void tokenStatus_withoutAStoredToken_isNotAccepted() {
        OutlineConnectionAdminService service = deferredService();
        when(connectionService.findActiveBearerToken(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());

        assertThat(service.tokenStatus(WS).accepted()).isFalse();
        verify(apiClient, never()).validateToken(any(), any());
    }

    @Test
    void tokenStatus_withoutActiveConnection_isNotFound() {
        OutlineConnectionAdminService service = deferredService();
        when(connectionService.findActive(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tokenStatus(WS)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void status_withoutActiveConnection_isNotFound() {
        OutlineConnectionAdminService service = deferredService();
        when(connectionService.findActive(WS, IntegrationKind.OUTLINE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.status(WS)).isInstanceOf(EntityNotFoundException.class);
        verify(syncScheduler, never()).syncWorkspaceNow(WS);
    }
}
