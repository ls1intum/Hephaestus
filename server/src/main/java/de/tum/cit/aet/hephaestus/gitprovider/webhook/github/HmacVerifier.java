package de.tum.cit.aet.hephaestus.gitprovider.webhook.github;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Constant-time HMAC-SHA256 verifier for GitHub's {@code X-Hub-Signature-256} header.
 *
 * <p>SHA-1 ({@code X-Hub-Signature}) is intentionally NOT accepted — accepting it alongside
 * SHA-256 is a known downgrade primitive. Comparison uses
 * {@link MessageDigest#isEqual(byte[], byte[])}, which is constant-time and length-tolerant.
 */
public final class HmacVerifier {

    private static final String SHA256_PREFIX = "sha256=";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacVerifier() {}

    public static boolean verify(String signatureHeader, String secret, byte[] body) {
        if (signatureHeader == null || signatureHeader.isEmpty() || secret == null || secret.isEmpty()) {
            return false;
        }
        if (!signatureHeader.startsWith(SHA256_PREFIX)) {
            return false;
        }

        byte[] computed;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            computed = mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }

        byte[] expected = (SHA256_PREFIX + HexFormat.of().formatHex(computed)).getBytes(StandardCharsets.UTF_8);
        byte[] header = signatureHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(header, expected);
    }
}
