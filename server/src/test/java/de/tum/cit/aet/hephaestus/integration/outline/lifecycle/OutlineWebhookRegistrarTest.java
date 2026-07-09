package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Correctness of the change-notification subscription lifecycle: a first reconcile registers a subscription
 * and stores its id + generated secret; an already-registered workspace is left untouched; and revoke tears
 * the subscription down. Registration is gated on a configured external URL.
 */
class OutlineWebhookRegistrarTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;
    private static final String SERVER_URL = "https://outline.example.test";
    private static final String EXTERNAL_URL = "https://heph.example.com/";

    @Mock
    private ConnectionService connectionService;

    @Mock
    private OutlineApiClient outlineApiClient;

    @Mock
    private Connection connection;

    private OutlineWebhookRegistrar registrar(String externalUrl) {
        return new OutlineWebhookRegistrar(
            connectionService,
            outlineApiClient,
            new EncryptedStringConverter(),
            externalUrl
        );
    }

    private void stubActiveConnection(ConnectionConfig config) {
        when(connection.getConfig()).thenReturn(config);
        when(connectionService.findActive(WORKSPACE_ID, IntegrationKind.OUTLINE)).thenReturn(Optional.of(connection));
    }

    @Test
    void registerIfNeeded_registersAndStoresSubscription() {
        stubActiveConnection(new ConnectionConfig.OutlineConfig(SERVER_URL, Set.of("col"), null, null, Set.of()));
        when(connectionService.findActiveBearerToken(WORKSPACE_ID, IntegrationKind.OUTLINE)).thenReturn(
            Optional.of(new BearerToken("tok", null))
        );
        when(
            outlineApiClient.createWebhookSubscription(
                eq(SERVER_URL),
                eq("tok"),
                anyString(),
                anyString(),
                anyString(),
                anyList()
            )
        ).thenReturn("sub-99");

        registrar(EXTERNAL_URL).registerIfNeeded(WORKSPACE_ID);

        // The delivery URL is the external URL (trailing slash trimmed) + the unified webhook path.
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> secret = ArgumentCaptor.forClass(String.class);
        verify(outlineApiClient).createWebhookSubscription(
            eq(SERVER_URL),
            eq("tok"),
            anyString(),
            url.capture(),
            secret.capture(),
            anyList()
        );
        assertThat(url.getValue()).isEqualTo("https://heph.example.com/webhooks/outline");
        assertThat(secret.getValue()).hasSize(64); // 256-bit hex

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UnaryOperator<ConnectionConfig>> mutator = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(connectionService).updateConfig(eq(WORKSPACE_ID), eq(IntegrationKind.OUTLINE), mutator.capture());
        ConnectionConfig.OutlineConfig updated = (ConnectionConfig.OutlineConfig) mutator
            .getValue()
            .apply(new ConnectionConfig.OutlineConfig(SERVER_URL, Set.of("col"), null, null, Set.of()));
        assertThat(updated.webhookSubscriptionId()).isEqualTo("sub-99");
        assertThat(updated.webhookSecret()).isEqualTo(secret.getValue());
    }

    @Test
    void registerIfNeeded_skipsWhenExternalUrlMissing() {
        registrar("").registerIfNeeded(WORKSPACE_ID);

        verify(connectionService, never()).findActive(anyLongMatcher(), any());
    }

    @Test
    void registerIfNeeded_skipsWhenAlreadyRegistered() {
        stubActiveConnection(
            new ConnectionConfig.OutlineConfig(SERVER_URL, Set.of("col"), "existing-sub", "sec", Set.of())
        );

        registrar(EXTERNAL_URL).registerIfNeeded(WORKSPACE_ID);

        verify(outlineApiClient, never()).createWebhookSubscription(any(), any(), any(), any(), any(), any());
        verify(connectionService, never()).updateConfig(anyLongMatcher(), any(), any());
    }

    @Test
    void deregister_deletesSubscriptionAndClearsFields() {
        stubActiveConnection(new ConnectionConfig.OutlineConfig(SERVER_URL, Set.of("col"), "sub-99", "sec", Set.of()));
        when(connectionService.findActiveBearerToken(WORKSPACE_ID, IntegrationKind.OUTLINE)).thenReturn(
            Optional.of(new BearerToken("tok", null))
        );

        registrar(EXTERNAL_URL).deregister(WORKSPACE_ID);

        verify(outlineApiClient).deleteWebhookSubscription(SERVER_URL, "tok", "sub-99");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UnaryOperator<ConnectionConfig>> mutator = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(connectionService).updateConfig(eq(WORKSPACE_ID), eq(IntegrationKind.OUTLINE), mutator.capture());
        ConnectionConfig.OutlineConfig cleared = (ConnectionConfig.OutlineConfig) mutator
            .getValue()
            .apply(new ConnectionConfig.OutlineConfig(SERVER_URL, Set.of("col"), "sub-99", "sec", Set.of()));
        assertThat(cleared.webhookSubscriptionId()).isNull();
        assertThat(cleared.webhookSecret()).isNull();
    }

    @Test
    void deregister_skipsWhenNoSubscriptionStored() {
        stubActiveConnection(new ConnectionConfig.OutlineConfig(SERVER_URL, Set.of("col"), null, null, Set.of()));

        registrar(EXTERNAL_URL).deregister(WORKSPACE_ID);

        verify(outlineApiClient, never()).deleteWebhookSubscription(any(), any(), any());
    }

    private static long anyLongMatcher() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
