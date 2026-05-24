package de.tum.cit.aet.hephaestus.integration.spi;

import java.util.Map;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * Per-kind signing-secret resolution.
 *
 * <p>Different vendors scope their webhook signing secret differently:
 * <ul>
 *   <li>GitHub: APP_GLOBAL — one shared {@code hephaestus.webhook.secret}
 *   <li>GitLab plaintext: APP_GLOBAL
 *   <li>GitLab {@code whsec_*} HMAC: WORKSPACE — per-Connection in {@code GitLabConfig.signingSecret}
 *   <li>Slack: APP_GLOBAL — one shared {@code hephaestus.slack.signing-secret}
 *   <li>Outline: SUBSCRIPTION — per-webhook in {@code integration_webhook_subscription}
 * </ul>
 */
public interface WebhookSecretSource {

    IntegrationKind kind();

    Scope scope();

    /**
     * Resolves the signing secret bytes for an incoming webhook.
     *
     * @param lookup what we know about the request (may be partial pre-verification)
     */
    Optional<byte[]> getSecret(SecretLookup lookup);

    enum Scope {
        /** One secret for all workspaces (GitHub, GitLab plaintext, Slack signing secret). */
        APP_GLOBAL,
        /** Per-workspace secret (GitLab {@code whsec_*} HMAC). */
        WORKSPACE,
        /** Per-webhook-subscription (Outline, Microsoft Graph). */
        SUBSCRIPTION
    }

    /** Best-effort identification of the request before signature verification. */
    record SecretLookup(
        @Nullable Long workspaceId,
        @Nullable String subscriptionId,
        Map<String, String> headers
    ) {
    }
}
