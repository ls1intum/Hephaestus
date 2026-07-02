package de.tum.cit.aet.hephaestus.integration.slack.events;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Verifies inbound Slack Events API requests: HMAC-SHA256 over {@code "v0:{timestamp}:{rawBody}"} keyed by the
 * app signing secret, compared constant-time to the {@code X-Slack-Signature} header, with a 300&nbsp;s replay
 * window on {@code X-Slack-Request-Timestamp} (Slack's documented scheme). Inert (rejects everything) when no
 * signing secret is configured.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SlackSignatureVerifier.class);
    private static final long MAX_SKEW_SECONDS = 300;
    private static final String HMAC_ALG = "HmacSHA256";

    private final byte[] signingSecret;
    private final boolean configured;

    public SlackSignatureVerifier(@Value("${hephaestus.integration.slack.signing-secret:}") String signingSecret) {
        this.configured = signingSecret != null && !signingSecret.isBlank();
        this.signingSecret = configured ? signingSecret.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!configured) {
            log.warn("Slack signing secret not set — inbound /slack/events will reject all requests.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * @param timestamp the {@code X-Slack-Request-Timestamp} header (unix seconds)
     * @param signature the {@code X-Slack-Signature} header ({@code v0=<hex>})
     * @param rawBody   the exact bytes of the request body (must not be re-serialized)
     * @param nowEpochSeconds current time for the replay-window check
     */
    public boolean verify(String timestamp, String signature, byte[] rawBody, long nowEpochSeconds) {
        if (!configured || timestamp == null || signature == null || rawBody == null) {
            return false;
        }
        final long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > MAX_SKEW_SECONDS) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALG));
            mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
            byte[] digest = mac.doFinal(rawBody);
            String expected = "v0=" + HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("Slack signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
