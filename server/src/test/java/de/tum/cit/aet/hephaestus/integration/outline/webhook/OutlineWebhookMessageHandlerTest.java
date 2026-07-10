package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService.OutlineSubscription;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

/**
 * The consumer-side handler resolves the body's subscription id to its workspace and routes on the
 * body's event name: {@code documents.*} with a payload id → targeted document refresh, without one →
 * whole-workspace reconcile fallback; {@code collections.*} → catalog refresh; anything else is
 * acknowledged without work. A delivery whose subscription no longer resolves (disconnected between
 * publish and consume) is a no-op rather than a NAK loop.
 */
class OutlineWebhookMessageHandlerTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    private OutlineWebhookMessageHandler handler() {
        return new OutlineWebhookMessageHandler(connectionService, syncScheduler, JsonMapper.builder().build());
    }

    private static Message message(String subscriptionId, String event, String payloadId) {
        Message msg = Mockito.mock(Message.class);
        String payload = payloadId == null ? "{}" : "{\"id\":\"" + payloadId + "\"}";
        when(msg.getData()).thenReturn(
            (
                "{\"webhookSubscriptionId\":\"" +
                subscriptionId +
                "\",\"event\":\"" +
                event +
                "\",\"payload\":" +
                payload +
                "}"
            ).getBytes(StandardCharsets.UTF_8)
        );
        return msg;
    }

    private void resolves(String subscriptionId, long workspaceId) {
        when(connectionService.findOutlineSubscription(subscriptionId)).thenReturn(
            Optional.of(new OutlineSubscription(workspaceId, "secret"))
        );
    }

    @Test
    void key_isTheCollapsedDocumentKey() {
        assertThat(handler().key().kind()).isEqualTo(IntegrationKind.OUTLINE);
        assertThat(handler().key().eventType()).isEqualTo(OutlineWebhookMessageHandler.EVENT_TYPE);
    }

    @Test
    void documentEventWithPayloadId_refreshesThatDocument() {
        resolves("sub-1", 42L);

        handler().onMessage(message("sub-1", "documents.update", "doc-9"));

        verify(syncScheduler).refreshDocumentNow(42L, "documents.update", "doc-9");
        verify(syncScheduler, never()).syncWorkspaceNow(Mockito.anyLong());
    }

    @Test
    void documentDeleteEvent_routesTheEventNameThrough() {
        resolves("sub-1", 42L);

        handler().onMessage(message("sub-1", "documents.delete", "doc-9"));

        verify(syncScheduler).refreshDocumentNow(42L, "documents.delete", "doc-9");
    }

    @Test
    void documentEventWithoutPayloadId_fallsBackToWorkspaceReconcile() {
        resolves("sub-1", 42L);

        handler().onMessage(message("sub-1", "documents.update", null));

        verify(syncScheduler).syncWorkspaceNow(42L);
        verify(syncScheduler, never()).refreshDocumentNow(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void collectionEvent_refreshesTheCatalog() {
        resolves("sub-1", 42L);

        handler().onMessage(message("sub-1", "collections.delete", "col-3"));

        verify(syncScheduler).refreshCollectionCatalogNow(42L, "collections.delete", "col-3");
    }

    @Test
    void collectionEventWithoutPayloadId_passesNull() {
        resolves("sub-1", 42L);

        handler().onMessage(message("sub-1", "collections.update", null));

        verify(syncScheduler).refreshCollectionCatalogNow(42L, "collections.update", null);
    }

    @Test
    void unknownEvent_isAcknowledgedWithoutWork() {
        resolves("sub-1", 42L);

        handler().onMessage(message("sub-1", "webhookSubscriptions.update", "x"));

        verifyNoInteractions(syncScheduler);
    }

    @Test
    void unresolvableSubscription_isNoOp() {
        when(connectionService.findOutlineSubscription("gone")).thenReturn(Optional.empty());

        handler().onMessage(message("gone", "documents.update", "doc-9"));

        verifyNoInteractions(syncScheduler);
    }
}
