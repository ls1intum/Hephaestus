package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Per-subscription signing-secret source for Outline.
 *
 * <p>Outline issues a unique signing secret per webhook subscription. The full
 * implementation needs:
 * <ol>
 *   <li>An {@code integration_webhook_subscription} repository to look up the
 *       row by {@code subscriptionId}.
 *   <li>The credential-converter / AES-GCM decryptor to unseal the persisted secret.
 * </ol>
 *
 * <p>Both land as follow-ups; for #1198 this source returns {@link Optional#empty()}
 * unconditionally. The verifier surfaces this as {@code MissingSignature} so the
 * pipeline rejects unauthenticated requests cleanly while we stand up the
 * subscription store.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineWebhookSecretSource implements WebhookSecretSource {

    private static final Logger log = LoggerFactory.getLogger(OutlineWebhookSecretSource.class);

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
        // TODO(#1203): resolve via integration_webhook_subscription repository +
        // AES-GCM decryption once the subscription store lands. Verifier returns
        // MissingSignature in the meantime — fail closed.
        log.debug("Outline webhook secret resolution stub: subscription={}", lookup.subscriptionId());
        return Optional.empty();
    }
}
