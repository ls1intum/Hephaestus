package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJob;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineSyncProgressListener;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Connect-time reactions: an Outline activation registers the change-notification subscription and
 * kicks the initial recency sync immediately, recorded as an {@code INITIAL}/{@code LIFECYCLE} job
 * through {@link SyncJobService} (replacing the plain {@code syncScheduler.syncWorkspaceNow} call this
 * listener used before the sync-observability wiring); a deactivation tears the subscription down by
 * connection id. Events for other kinds are ignored, and the async listener never rethrows — the
 * periodic reconcile is the safety net.
 */
class OutlineConnectionStateListenerTest extends BaseUnitTest {

    @Mock
    private OutlineWebhookRegistrar webhookRegistrar;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    @Mock
    private SyncJobService syncJobService;

    @InjectMocks
    private OutlineConnectionStateListener listener;

    /**
     * Stubs {@code syncJobService.run(...)} to actually invoke the body, as the real template would.
     * {@link SyncJobHandle} is a {@code final} class with a package-private constructor (by design — only
     * {@code SyncJobService} builds one), so it cannot be mocked or instantiated here; {@code null} is
     * safe because {@link de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineSyncProgress#adapt}
     * only closes over the handle — nothing in this test path dereferences it (the mocked
     * {@code syncScheduler} never calls the listener it was handed).
     */
    @SuppressWarnings("unchecked")
    private void runJobsSynchronously() {
        doAnswer(invocation -> {
            Consumer<SyncJobHandle> body = invocation.getArgument(1);
            body.accept(null);
            return null;
        })
            .when(syncJobService)
            .run(any(SyncJobRequest.class), any());
    }

    @Test
    void outlineActivation_registersSubscriptionAndRecordsInitialLifecycleJob() {
        runJobsSynchronously();

        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.OUTLINE));

        verify(webhookRegistrar).ensureSubscription(5L);
        ArgumentCaptor<SyncJobRequest> requestCaptor = ArgumentCaptor.forClass(SyncJobRequest.class);
        verify(syncJobService).run(requestCaptor.capture(), any());
        SyncJobRequest request = requestCaptor.getValue();
        assertThat(request.workspaceId()).isEqualTo(5L);
        assertThat(request.connectionId()).isEqualTo(42L);
        assertThat(request.kind()).isEqualTo(IntegrationKind.OUTLINE);
        assertThat(request.type()).isEqualTo(SyncJobType.INITIAL);
        assertThat(request.trigger()).isEqualTo(SyncJobTrigger.LIFECYCLE);
        // The job body is exactly the workspace sync, threaded with a progress listener.
        verify(syncScheduler).syncWorkspaceNow(eq(5L), any(OutlineSyncProgressListener.class));
    }

    @Test
    void nonOutlineActivation_isIgnored() {
        listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.GITHUB));

        verifyNoInteractions(webhookRegistrar, syncScheduler, syncJobService);
    }

    @Test
    void outlineActivation_jobAlreadyActive_isSkippedWithoutPropagating() {
        SyncJob activeJob = mock(SyncJob.class);
        Connection activeConnection = mock(Connection.class);
        when(activeConnection.getId()).thenReturn(42L);
        when(activeJob.getConnection()).thenReturn(activeConnection);
        when(activeJob.getId()).thenReturn(99L);
        doThrow(new SyncJobConflictException(activeJob)).when(syncJobService).run(any(), any());

        assertThatCode(() ->
            listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.OUTLINE))
        ).doesNotThrowAnyException();
        // The conflict is absorbed before the body ever runs — no direct scheduler call either.
        verify(syncScheduler, never()).syncWorkspaceNow(eq(5L), any(OutlineSyncProgressListener.class));
    }

    @Test
    void outlineDeactivation_deregistersByConnectionId() {
        listener.onDeactivated(new ConnectionLifecycleEvent.Deactivated(42L, 5L, IntegrationKind.OUTLINE));

        verify(webhookRegistrar).deregister(5L, 42L);
        verifyNoInteractions(syncScheduler, syncJobService);
    }

    @Test
    void nonOutlineDeactivation_isIgnored() {
        listener.onDeactivated(new ConnectionLifecycleEvent.Deactivated(42L, 5L, IntegrationKind.SLACK));

        verifyNoInteractions(webhookRegistrar, syncScheduler, syncJobService);
    }

    @Test
    void connectTimeFailure_neverPropagatesOffTheAsyncThread() {
        doThrow(new IllegalStateException("outline is down")).when(webhookRegistrar).ensureSubscription(5L);

        assertThatCode(() ->
            listener.onActivated(new ConnectionLifecycleEvent.Activated(42L, 5L, IntegrationKind.OUTLINE))
        ).doesNotThrowAnyException();
        verify(syncJobService, times(0)).run(any(), any());
    }
}
