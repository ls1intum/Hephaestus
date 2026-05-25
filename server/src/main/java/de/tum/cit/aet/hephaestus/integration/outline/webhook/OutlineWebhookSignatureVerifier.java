package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import tools.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Outline webhook signature verification.
 *
 * <p>Outline signs each delivery with HMAC-SHA256 over the raw request body, hex-encoded,
 * delivered in the {@code Outline-Signature} header. The signing secret is per-subscription
 * (Outline mints one when the webhook is created) — distinct from Slack's app-global secret
 * and GitHub's app-global secret.
 *
 * <p>The initial-subscription verification handshake (where Outline POSTs the new secret
 * in the body before normal delivery starts) requires the {@code CaptureSecret} verdict
 * to persist the freshly-issued secret. That branch is intentionally TODO for #1198:
 * the subscription store + AES-GCM converter must land first so we have a place to put
 * the captured bytes. Until then, the verifier sticks to the steady-state HMAC path.
 *
 * <p>{@link WebhookSecretSource} returns empty until the subscription store lands; that
 * surfaces here as {@link VerificationResult.MissingSignature} — fail closed.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(OutlineWebhookSignatureVerifier.class);

    private static final String HEADER_SIGNATURE = "Outline-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final OutlineWebhookSecretSource secretSource;

    public OutlineWebhookSignatureVerifier(ObjectMapper objectMapper, OutlineWebhookSecretSource secretSource) {
        this.objectMapper = objectMapper;
        this.secretSource = secretSource;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public VerificationResult verify(WebhookRequest request) {
        byte[] body = request.body();
        Map<String, String> headers = request.headers();

        // TODO(#1203): detect Outline's initial-subscription verification handshake
        // (body shape carries the newly-issued signing secret) and return
        // CaptureSecret(subscriptionId, secret, RespondImmediately(200, "application/json", "{}"))
        // so the pipeline persists the secret before responding. We deliberately skip
        // shape-sniffing here because the handshake format is opt-in and easy to spoof
        // without a vetted reference. Use the {@code objectMapper} field for the parse
        // once the spec is locked. (Field is held to keep the wiring point obvious.)
        if (objectMapper == null) {
            // Defensive — Spring guarantees the bean is injected, but a future test
            // double should not be allowed to construct with null.
            return new VerificationResult.Invalid("object mapper unavailable");
        }

        String signature = headerIgnoreCase(headers, HEADER_SIGNATURE);
        if (signature == null || signature.isBlank()) {
            return new VerificationResult.MissingSignature();
        }

        Optional<byte[]> secret = secretSource.getSecret(
            new SecretLookup(request.subscriptionId(), headers)
        );
        if (secret.isEmpty()) {
            // No subscription registered yet → fail closed. Different from Slack's
            // "Invalid(unconfigured)": Outline secrets are per-subscription, so "not yet
            // captured" is operationally indistinguishable from "no such subscription".
            log.debug("Outline secret unavailable for subscription={}; rejecting as MissingSignature",
                request.subscriptionId());
            return new VerificationResult.MissingSignature();
        }

        byte[] computed;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.get(), HMAC_SHA256));
            computed = mac.doFinal(body == null ? new byte[0] : body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HmacSHA256 unavailable", e);
            return new VerificationResult.Invalid("hmac unavailable");
        }
        String expectedHex = HexFormat.of().formatHex(computed);
        boolean ok = MessageDigest.isEqual(
            expectedHex.getBytes(StandardCharsets.UTF_8),
            signature.trim().getBytes(StandardCharsets.UTF_8)
        );
        if (!ok) {
            return new VerificationResult.Invalid("signature mismatch");
        }
        return new VerificationResult.Verified();
    }

    private static String headerIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String direct = headers.get(name);
        if (direct != null) return direct;
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
