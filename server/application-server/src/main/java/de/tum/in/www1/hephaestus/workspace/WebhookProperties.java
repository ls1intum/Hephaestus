package de.tum.in.www1.hephaestus.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for webhook auto-registration.
 *
 * <p>These properties are shared with the webhook-ingest service — the same
 * {@code secret} is used by both services. The application-server uses the secret
 * when registering webhooks with GitLab; the webhook-ingest uses it to verify
 * incoming webhook payloads.
 *
 * @param externalUrl                  publicly reachable base URL for webhook delivery
 *                                     (e.g., {@code https://app.example.com/webhooks})
 * @param secret                       global webhook secret shared with webhook-ingest
 * @param tokenRotationThresholdDays   rotate PAT when it expires within this many days (default: 7)
 * @param tokenRotationValidityDays    validity period in days for newly rotated tokens (default: 90)
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.webhook")
public record WebhookProperties(
    @Nullable String externalUrl,
    @Nullable String secret,
    @DefaultValue("7") int tokenRotationThresholdDays,
    @DefaultValue("90") int tokenRotationValidityDays
) {
    private static final Logger log = LoggerFactory.getLogger(WebhookProperties.class);

    static final int MIN_SECRET_LENGTH = 32;

    /**
     * Returns {@code true} when both the external URL and secret are configured,
     * meaning webhook auto-registration can be attempted.
     */
    public boolean isConfigured() {
        if (externalUrl == null || externalUrl.isBlank()) {
            return false;
        }
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            log.warn(
                "Webhook secret is configured but too short ({} chars, minimum {}). " +
                    "Webhook auto-registration is disabled.",
                secret.length(),
                MIN_SECRET_LENGTH
            );
            return false;
        }
        return true;
    }
}
