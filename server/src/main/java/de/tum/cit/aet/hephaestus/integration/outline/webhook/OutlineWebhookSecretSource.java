package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWebhookRole;
import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Subscription-scoped signing-secret source for inbound Outline webhooks
 * ({@link WebhookSecretSource.Scope#SUBSCRIPTION}). The subscription id is carried in the event
 * <em>body</em> (not a header) as an untrusted routing key, so this source parses it out of
 * {@link SecretLookup#body()} and resolves the stored secret of the ACTIVE Outline Connection that
 * registered it. A forged id matches no connection, so the downstream HMAC check fails.
 *
 * <p>Webhook-role only ({@code SlackWebhookSignatureVerifier} precedent): the bean only serves
 * inbound deliveries, so the app-server and worker roles have no business holding a component whose
 * single job is handing out signing secrets.
 */
@Component
@ConditionalOnWebhookRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineWebhookSecretSource implements WebhookSecretSource {

    private final ConnectionService connectionService;
    private final EncryptedStringConverter secretCipher;
    private final ObjectMapper objectMapper;

    public OutlineWebhookSecretSource(
        ConnectionService connectionService,
        EncryptedStringConverter secretCipher,
        ObjectMapper objectMapper
    ) {
        this.connectionService = connectionService;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public Scope scope() {
        return Scope.SUBSCRIPTION;
    }

    @Override
    public Optional<byte[]> getSecret(SecretLookup lookup) {
        String subscriptionId = extractSubscriptionId(lookup.body());
        if (subscriptionId.isBlank()) {
            return Optional.empty();
        }
        return connectionService
            .findOutlineSubscription(subscriptionId)
            .map(sub -> secretCipher.convertToEntityAttribute(sub.signingSecret()).getBytes(StandardCharsets.UTF_8));
    }

    private String extractSubscriptionId(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            return objectMapper.readTree(body).path("webhookSubscriptionId").asString("");
        } catch (RuntimeException e) {
            // Unparseable body → no subscription → no secret. The pipeline rejects with 401.
            return "";
        }
    }
}
