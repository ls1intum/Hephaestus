package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Per-subscription signing-secret source for Outline. Returns empty until #1203 lands
 * the subscription store + AES-GCM decryption path. The verifier surfaces this as
 * {@code MissingSignature} so the pipeline fails closed.
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
        // #1203 wires the per-subscription store + AES-GCM decryption. Verifier returns
        // MissingSignature in the meantime — fail closed.
        return Optional.empty();
    }
}
