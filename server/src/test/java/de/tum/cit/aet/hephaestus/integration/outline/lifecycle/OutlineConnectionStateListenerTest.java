package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.core.events.ConnectionLifecycleEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Connect-time reactions: an Outline activation registers the change-notification subscription and
 * kicks the initial recency sync immediately; a deactivation tears the subscription down by connection
 * id (the row already left ACTIVE). Events for other kinds are ignored, and the async listener never
 * rethrows — the periodic reconcile is the safety net.
 */
class OutlineConnectionStateListenerTest extends BaseUnitTest {

    @Mock
    private OutlineWebhookRegistrar webhookRegistrar;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    @InjectMocks
    private OutlineConnectionStateListener listener;

    @Test
    void outlineActivation_registersSubscriptionAndKicksInitialSync() {
        listener.onActivated(new ConnectionLifecycleEvent.Activated(5L, 42L, IntegrationKind.OUTLINE));

        verify(webhookRegistrar).ensureSubscription(42L);
        verify(syncScheduler).syncWorkspaceNow(42L);
    }

    @Test
    void nonOutlineActivation_isIgnored() {
        listener.onActivated(new ConnectionLifecycleEvent.Activated(5L, 42L, IntegrationKind.GITHUB));

        verifyNoInteractions(webhookRegistrar, syncScheduler);
    }

    @Test
    void outlineDeactivation_deregistersByConnectionId() {
        listener.onDeactivated(new ConnectionLifecycleEvent.Deactivated(5L, 42L, IntegrationKind.OUTLINE));

        verify(webhookRegistrar).deregister(42L, 5L);
        verifyNoInteractions(syncScheduler);
    }

    @Test
    void nonOutlineDeactivation_isIgnored() {
        listener.onDeactivated(new ConnectionLifecycleEvent.Deactivated(5L, 42L, IntegrationKind.SLACK));

        verifyNoInteractions(webhookRegistrar, syncScheduler);
    }

    @Test
    void connectTimeFailure_neverPropagatesOffTheAsyncThread() {
        doThrow(new IllegalStateException("outline is down")).when(webhookRegistrar).ensureSubscription(42L);

        assertThatCode(() ->
            listener.onActivated(new ConnectionLifecycleEvent.Activated(5L, 42L, IntegrationKind.OUTLINE))
        ).doesNotThrowAnyException();
    }
}
