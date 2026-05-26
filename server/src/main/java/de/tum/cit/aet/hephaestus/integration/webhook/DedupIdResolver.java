package de.tum.cit.aet.hephaestus.integration.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds {@code prefix-{first-32-hex-chars-of-sha256(body || extra)}}, used as the
 * {@code Nats-Msg-Id} so JetStream can dedup duplicate deliveries. 128 bits give ~2^64 collision
 * resistance — sufficient inside the 2-minute dedup window.
 */
public final class DedupIdResolver {

    private static final int HEX_LENGTH = 32;

    private DedupIdResolver() {}

    public static String build(String prefix, byte[] body, String extra) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        digest.update(body);
        if (extra != null && !extra.isEmpty()) {
            digest.update(extra.getBytes(StandardCharsets.UTF_8));
        }
        return prefix + "-" + HexFormat.of().formatHex(digest.digest()).substring(0, HEX_LENGTH);
    }

    public static String build(String prefix, byte[] body) {
        return build(prefix, body, null);
    }
}
