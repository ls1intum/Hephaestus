package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * App-global signing-secret source for Slack.
 *
 * <p>Slack issues exactly one signing secret per Slack app, shared across all installations.
 * Resolution order:
 * <ol>
 *   <li>{@code hephaestus.slack.signing-secret} — Slack-specific override
 *   <li>{@code hephaestus.webhook.secret} — pre-existing shared infrastructure secret
 * </ol>
 *
 * <p>Returns {@link Optional#empty()} when neither property is set; the verifier
 * surfaces this as {@code Invalid("signing secret unconfigured")} so a misconfigured
 * deployment fails closed rather than letting unsigned requests through.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackWebhookSecretSource implements WebhookSecretSource {

    private final String configuredSecret;

    public SlackWebhookSecretSource(
        @Value("${hephaestus.slack.signing-secret:${hephaestus.webhook.secret:}}") String configuredSecret
    ) {
        this.configuredSecret = configuredSecret;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public Scope scope() {
        return Scope.APP_GLOBAL;
    }

    @Override
    public Optional<byte[]> getSecret(SecretLookup lookup) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configuredSecret.getBytes(StandardCharsets.UTF_8));
    }
}
