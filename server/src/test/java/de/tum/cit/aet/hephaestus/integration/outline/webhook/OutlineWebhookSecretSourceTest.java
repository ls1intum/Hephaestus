package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService.OutlineSubscription;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

/**
 * The subscription-scoped secret source parses the body-carried subscription id (an untrusted routing
 * key) and delegates resolution to {@link ConnectionService#findOutlineSubscription}. The matching
 * logic itself is covered in {@code ConnectionServiceTest}; here we pin the SPI contract: scope, body
 * parsing, and empty-on-miss.
 */
class OutlineWebhookSecretSourceTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    private OutlineWebhookSecretSource secretSource() {
        return new OutlineWebhookSecretSource(
            connectionService,
            new EncryptedStringConverter(),
            JsonMapper.builder().build()
        );
    }

    private static SecretLookup lookup(String subscriptionId) {
        byte[] body = (
            "{\"webhookSubscriptionId\":\"" +
            subscriptionId +
            "\",\"event\":\"documents.update\"}"
        ).getBytes(StandardCharsets.UTF_8);
        return new SecretLookup(Map.of(), body);
    }

    @Test
    void scope_isSubscription() {
        assertThat(secretSource().scope()).isEqualTo(WebhookSecretSource.Scope.SUBSCRIPTION);
    }

    @Test
    void getSecret_returnsStoredSecretBytesForResolvedSubscription() {
        when(connectionService.findOutlineSubscription("sub-b")).thenReturn(
            Optional.of(new OutlineSubscription(2L, "secret-b"))
        );

        Optional<byte[]> secret = secretSource().getSecret(lookup("sub-b"));

        assertThat(secret).isPresent();
        assertThat(secret.get()).isEqualTo("secret-b".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getSecret_isEmptyWhenNoSubscriptionResolves() {
        when(connectionService.findOutlineSubscription("unknown")).thenReturn(Optional.empty());
        assertThat(secretSource().getSecret(lookup("unknown"))).isEmpty();
    }

    @Test
    void getSecret_isEmptyForAnUnparseableOrEmptyBody() {
        assertThat(secretSource().getSecret(new SecretLookup(Map.of(), new byte[0]))).isEmpty();
        assertThat(
            secretSource().getSecret(new SecretLookup(Map.of(), "not-json".getBytes(StandardCharsets.UTF_8)))
        ).isEmpty();
    }
}
