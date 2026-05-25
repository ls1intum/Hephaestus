package de.tum.cit.aet.hephaestus.integration.spi;

import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Per-kind webhook signature verification.
 *
 * <p>Three additional verdict variants beyond plan v3 absorb cross-integration realities:
 * <ul>
 *   <li>{@link VerificationResult.StaleTimestamp} — Slack's 5-minute replay window
 *   <li>{@link VerificationResult.RespondImmediately} — Slack {@code url_verification}
 *       challenge + Asana {@code X-Hook-Secret} echo handshake (response body served
 *       directly, never reaches NATS)
 *   <li>{@link VerificationResult.CaptureSecret} — Notion's verification flow ships the
 *       per-subscription signing secret in the handshake response body
 * </ul>
 */
public interface WebhookSignatureVerifier {

    IntegrationKind kind();

    VerificationResult verify(WebhookRequest request);

    /**
     * Workspace context isn't known at this layer — verifiers resolve it from headers /
     * payload when they need to look up a per-workspace secret. {@code subscriptionId} is
     * carried because Notion verification ships the signing secret in the handshake.
     */
    record WebhookRequest(
        byte[] body,
        Map<String, String> headers,
        @Nullable String subscriptionId
    ) {
    }

    sealed interface VerificationResult
        permits VerificationResult.Verified,
                VerificationResult.Invalid,
                VerificationResult.MissingSignature,
                VerificationResult.StaleTimestamp,
                VerificationResult.RespondImmediately,
                VerificationResult.CaptureSecret {

        record Verified() implements VerificationResult {}

        record Invalid(String reason) implements VerificationResult {}

        record MissingSignature() implements VerificationResult {}

        /** Replay protection — request timestamp drifted beyond the allowed window. */
        record StaleTimestamp(long driftSeconds) implements VerificationResult {}

        /**
         * Pipeline must respond directly with the given payload — request must NOT
         * be published to NATS. Used for Slack {@code url_verification} and Asana's
         * X-Hook-Secret echo.
         */
        record RespondImmediately(
            int statusCode,
            String contentType,
            byte[] body,
            Map<String, String> headers
        ) implements VerificationResult {
            public RespondImmediately(int statusCode, String contentType, byte[] body) {
                this(statusCode, contentType, body, Map.of());
            }
        }

        /**
         * Pipeline must persist the supplied per-subscription signing secret before
         * responding. Used for Notion webhooks: the signing secret arrives in the
         * verification request body and must be stored against the subscription id.
         */
        record CaptureSecret(String subscriptionId, byte[] secret, RespondImmediately response)
            implements VerificationResult {
        }
    }
}
