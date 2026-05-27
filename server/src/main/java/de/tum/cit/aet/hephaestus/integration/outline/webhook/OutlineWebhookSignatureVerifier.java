package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier;
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
 * <p>HMAC-SHA256 of the raw body, hex-encoded, in the {@code Outline-Signature} header.
 * Signing secrets are per-subscription; the {@link OutlineWebhookSecretSource} returns
 * empty until the subscription store lands in #1203, so verification fails closed.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(OutlineWebhookSignatureVerifier.class);

    private static final String HEADER_SIGNATURE = "Outline-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final OutlineWebhookSecretSource secretSource;

    public OutlineWebhookSignatureVerifier(OutlineWebhookSecretSource secretSource) {
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

        String signature = headerIgnoreCase(headers, HEADER_SIGNATURE);
        if (signature == null || signature.isBlank()) {
            return new VerificationResult.MissingSignature();
        }

        Optional<byte[]> secret = secretSource.getSecret(new SecretLookup(headers));
        if (secret.isEmpty()) {
            log.debug("Outline secret unavailable; rejecting as MissingSignature");
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
        return ok ? new VerificationResult.Verified() : new VerificationResult.Invalid("signature mismatch");
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
