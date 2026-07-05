package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * The consumer-side handler resolves the body's subscription id to its workspace and reconciles that
 * workspace; a delivery whose subscription no longer resolves (disconnected between publish and
 * consume) is a no-op rather than a reconcile.
 */
class OutlineWebhookMessageHandlerTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineDocumentSyncScheduler syncScheduler;

    private OutlineWebhookMessageHandler handler() {
        return new OutlineWebhookMessageHandler(connectionService, syncScheduler, JsonMapper.builder().build());
    }

    private static Message message(String subscriptionId) {
        Message msg = Mockito.mock(Message.class);
        when(msg.getData()).thenReturn(
            ("{\"webhookSubscriptionId\":\"" + subscriptionId + "\",\"event\":\"documents.update\"}").getBytes(
                StandardCharsets.UTF_8
            )
        );
        return msg;
    }

    @Test
    void key_isTheCollapsedDocumentKey() {
        org.assertj.core.api.Assertions.assertThat(handler().key().kind()).isEqualTo(IntegrationKind.OUTLINE);
        org.assertj.core.api.Assertions.assertThat(handler().key().eventType()).isEqualTo(
            OutlineWebhookMessageHandler.EVENT_TYPE
        );
    }

    @Test
    void onMessage_reconcilesTheResolvedWorkspace() {
        when(connectionService.findOutlineSubscription("sub-1")).thenReturn(
            Optional.of(new OutlineSubscription(42L, "secret"))
        );

        handler().onMessage(message("sub-1"));

        verify(syncScheduler).syncWorkspaceNow(42L);
    }

    @Test
    void onMessage_isNoOpWhenSubscriptionNoLongerResolves() {
        when(connectionService.findOutlineSubscription("gone")).thenReturn(Optional.empty());

        handler().onMessage(message("gone"));

        verify(syncScheduler, never()).syncWorkspaceNow(Mockito.anyLong());
    }
}
