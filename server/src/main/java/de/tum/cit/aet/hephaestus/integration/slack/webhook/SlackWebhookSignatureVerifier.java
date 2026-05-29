package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Slack webhook signature verification.
 *
 * <p>Two distinct paths:
 * <ul>
 *   <li>{@code url_verification} handshake — Slack pings the configured URL with
 *       {@code {"type":"url_verification","challenge":"..."}} BEFORE the app has any
 *       installation context. Per Slack's docs the handshake is unauthenticated; we
 *       echo the challenge in {@code text/plain} via {@link VerificationResult.RespondImmediately}.
 *   <li>Normal events — HMAC-SHA256 over {@code "v0:" + timestamp + ":" + body},
 *       hex-encoded, prefixed {@code "v0="}, compared constant-time against
 *       {@code X-Slack-Signature}. A 5-minute timestamp window blocks replay.
 * </ul>
 *
 * <p>Secret resolution goes through {@link WebhookSecretSource} (APP_GLOBAL). A missing
 * secret returns {@code Invalid("signing secret unconfigured")} so the pipeline fails closed
 * rather than silently accepting unauthenticated traffic.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SlackWebhookSignatureVerifier.class);

    private static final String HEADER_SIGNATURE = "X-Slack-Signature";
    private static final String HEADER_TIMESTAMP = "X-Slack-Request-Timestamp";
    private static final String SIGNATURE_PREFIX = "v0=";
    private static final String HMAC_SHA256 = "HmacSHA256";
    /** Slack-recommended replay window: 5 minutes. */
    private static final long MAX_DRIFT_SECONDS = 300L;

    private final ObjectMapper objectMapper;
    private final SlackWebhookSecretSource secretSource;

    public SlackWebhookSignatureVerifier(ObjectMapper objectMapper, SlackWebhookSecretSource secretSource) {
        this.objectMapper = objectMapper;
        this.secretSource = secretSource;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public VerificationResult verify(WebhookRequest request) {
        byte[] body = request.body();
        Map<String, String> headers = request.headers();

        // 1. url_verification handshake bypasses signature checks (Slack docs).
        Optional<String> challenge = readUrlVerificationChallenge(body);
        if (challenge.isPresent()) {
            return new VerificationResult.RespondImmediately(
                200,
                "text/plain",
                challenge.get().getBytes(StandardCharsets.UTF_8)
            );
        }

        // 2. Headers required.
        String signature = headerIgnoreCase(headers, HEADER_SIGNATURE);
        String timestampStr = headerIgnoreCase(headers, HEADER_TIMESTAMP);
        if (signature == null || signature.isBlank() || timestampStr == null || timestampStr.isBlank()) {
            return new VerificationResult.MissingSignature();
        }

        // 3. Replay window check.
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr.trim());
        } catch (NumberFormatException e) {
            return new VerificationResult.Invalid("malformed timestamp");
        }
        long now = Instant.now().getEpochSecond();
        long drift = Math.abs(now - timestamp);
        if (drift > MAX_DRIFT_SECONDS) {
            return new VerificationResult.StaleTimestamp(drift);
        }

        // 4. Secret.
        Optional<byte[]> secret = secretSource.getSecret(new SecretLookup(headers));
        if (secret.isEmpty()) {
            log.warn(
                "Slack signing secret unconfigured — set hephaestus.integration.slack.signing-secret or hephaestus.webhook.secret"
            );
            return new VerificationResult.Invalid("signing secret unconfigured");
        }

        // 5. Compute + compare constant-time. Slack signs over the RAW request body bytes, so we
        // feed the "v0:<ts>:" prefix and the body straight into the Mac. Round-tripping the body
        // through `new String(body, UTF_8)` would map any non-UTF-8 byte to U+FFFD and re-encode
        // to different bytes, producing a wrong MAC and rejecting an otherwise-valid request.
        byte[] computedMac;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.get(), HMAC_SHA256));
            mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            computedMac = mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HmacSHA256 unavailable", e);
            return new VerificationResult.Invalid("hmac unavailable");
        }
        String expected = SIGNATURE_PREFIX + HexFormat.of().formatHex(computedMac);
        boolean ok = MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        );
        if (!ok) {
            return new VerificationResult.Invalid("signature mismatch");
        }
        return new VerificationResult.Verified();
    }

    private Optional<String> readUrlVerificationChallenge(byte[] body) {
        if (body == null || body.length == 0) return Optional.empty();
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) return Optional.empty();
            JsonNode typeNode = root.get("type");
            if (typeNode == null || !"url_verification".equals(typeNode.asText())) return Optional.empty();
            JsonNode challengeNode = root.get("challenge");
            if (challengeNode == null || !challengeNode.isTextual()) return Optional.empty();
            return Optional.of(challengeNode.asText());
        } catch (Exception e) {
            // Non-JSON bodies are normal for event-callback envelopes only when challenge
            // isn't present; falling through to the signature path is correct.
            return Optional.empty();
        }
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
