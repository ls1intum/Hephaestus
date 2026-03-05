package de.tum.in.www1.hephaestus.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 */
@ConfigurationProperties(prefix = "hephaestus.webhook")
public record WebhookProperties(String externalUrl, String secret, int tokenRotationThresholdDays) {
    /**
     * Returns {@code true} when both the external URL and secret are configured,
     * meaning webhook auto-registration can be attempted.
     */
    public boolean isConfigured() {
        return externalUrl != null && !externalUrl.isBlank() && secret != null && secret.length() >= 32;
    }
}
