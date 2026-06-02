package de.tum.cit.aet.hephaestus.integration.scm.gitlab.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSecretSource.SecretLookup;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dual-mode GitLab webhook signature verifier.
 *
 * <p>Two coexisting wire formats:
 * <ul>
 *   <li><b>Legacy plaintext</b> — {@code X-Gitlab-Token} header byte-equals the
 *       shared secret. This is GitLab's original webhook auth, still the default
 *       on all GitLab installs &lt; 19.0 and on workspaces that haven't opted
 *       into Standard Webhooks.
 *   <li><b>Standard Webhooks HMAC (GitLab 19.0+)</b> — {@code X-Gitlab-Signature}
 *       carries one or more {@code v1,<base64-hmac>} pairs comma-separated. The
 *       signing secret has the form {@code whsec_<base64>}; we strip the prefix,
 *       base64-decode the rest to get the MAC key, then compute
 *       {@code HMAC_SHA256(key, "<webhook-id>.<webhook-timestamp>.<body>")}.
 *       Replay protection: {@code webhook-timestamp} must be within
 *       {@link #TIMESTAMP_TOLERANCE} of now (the spec's 5-minute window).
 * </ul>
 *
 * <p>If a request carries BOTH headers, the signature header (modern path) takes
 * priority — this is the safer choice: an attacker who only knows the legacy
 * shared secret cannot forge an HMAC over an arbitrary body.
 *
 * <p>Reference: <a href="https://github.com/standard-webhooks/standard-webhooks/blob/main/spec/standard-webhooks.md">Standard Webhooks spec</a>.
 */
@Component
public class GitlabWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitlabWebhookSignatureVerifier.class);

    static final String HEADER_TOKEN = "x-gitlab-token";
    static final String HEADER_SIGNATURE = "x-gitlab-signature";
    static final String HEADER_WEBHOOK_ID = "webhook-id";
    static final String HEADER_WEBHOOK_TIMESTAMP = "webhook-timestamp";

    static final String WHSEC_PREFIX = "whsec_";
    static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Spec-mandated maximum clock skew. The spec calls for ±5 minutes; we apply it
     * symmetrically (future-dated bodies also fail).
     */
    static final Duration TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);

    private final WebhookSecretSource secretSource;
    private final Clock clock;

    @Autowired
    public GitlabWebhookSignatureVerifier(List<WebhookSecretSource> secretSources) {
        this(pickGitlabSource(secretSources), Clock.systemUTC());
    }

    /** Test-friendly constructor — direct injection of the source + clock. */
    GitlabWebhookSignatureVerifier(WebhookSecretSource secretSource, Clock clock) {
        this.secretSource = secretSource;
        this.clock = clock;
    }

    private static WebhookSecretSource pickGitlabSource(List<WebhookSecretSource> sources) {
        return sources
            .stream()
            .filter(s -> s.kind() == IntegrationKind.GITLAB)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No WebhookSecretSource bean registered for GITLAB"));
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public VerificationResult verify(WebhookRequest request) {
        Map<String, String> normalized = normalizeHeaders(request.headers());
        String signatureHeader = normalized.get(HEADER_SIGNATURE);
        String tokenHeader = normalized.get(HEADER_TOKEN);

        // Modern path takes priority when both headers are present. A request bearing
        // X-Gitlab-Signature is opting into Standard Webhooks — falling back to the
        // weaker plaintext token on signature mismatch would be a downgrade primitive.
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            return verifyWhsec(request, normalized, signatureHeader);
        }
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return verifyPlaintext(request, normalized, tokenHeader);
        }
        return new VerificationResult.MissingSignature();
    }

    private VerificationResult verifyPlaintext(
        WebhookRequest request,
        Map<String, String> headers,
        String tokenHeader
    ) {
        Optional<byte[]> secret = secretSource.getSecret(new SecretLookup(headers));
        if (secret.isEmpty()) {
            log.warn("GitLab plaintext verifier: no shared secret available");
            return new VerificationResult.Invalid("missing-secret");
        }
        // Hash both sides to fixed-width 32-byte digests before the constant-time compare.
        // A raw MessageDigest.isEqual over the byte arrays loops over the presented token's
        // length, so timing would leak the attacker-controlled token length (and a length
        // mismatch is trivially distinguishable) — narrowing brute-force. Hashing first makes
        // both inputs equal-length regardless of secret length, removing that side channel.
        byte[] tokenDigest = sha256(tokenHeader.getBytes(StandardCharsets.UTF_8));
        byte[] secretDigest = sha256(secret.get());
        if (tokenDigest == null || secretDigest == null) {
            return new VerificationResult.Invalid("hash-init-failed");
        }
        if (MessageDigest.isEqual(tokenDigest, secretDigest)) {
            return new VerificationResult.Verified();
        }
        return new VerificationResult.Invalid("token-mismatch");
    }

    private VerificationResult verifyWhsec(
        WebhookRequest request,
        Map<String, String> headers,
        String signatureHeader
    ) {
        String msgId = headers.get(HEADER_WEBHOOK_ID);
        String timestampHeader = headers.get(HEADER_WEBHOOK_TIMESTAMP);
        if (msgId == null || msgId.isBlank()) {
            return new VerificationResult.Invalid("missing-webhook-id");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return new VerificationResult.Invalid("missing-webhook-timestamp");
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return new VerificationResult.Invalid("malformed-webhook-timestamp");
        }

        long nowSeconds = clock.instant().getEpochSecond();
        long drift = Math.abs(nowSeconds - timestampSeconds);
        if (drift > TIMESTAMP_TOLERANCE.toSeconds()) {
            return new VerificationResult.StaleTimestamp(drift);
        }

        Optional<byte[]> secret = secretSource.getSecret(new SecretLookup(headers));
        if (secret.isEmpty()) {
            log.warn("GitLab whsec verifier: no signing secret available");
            return new VerificationResult.Invalid("missing-secret");
        }

        byte[] hmacKey = extractHmacKey(secret.get());
        if (hmacKey == null) {
            return new VerificationResult.Invalid("malformed-whsec-secret");
        }

        byte[] expectedMac = computeHmac(hmacKey, msgId, timestampHeader.trim(), request.body());
        if (expectedMac == null) {
            return new VerificationResult.Invalid("hmac-init-failed");
        }
        String expectedB64 = Base64.getEncoder().encodeToString(expectedMac);
        byte[] expectedBytes = expectedB64.getBytes(StandardCharsets.UTF_8);

        // GitLab sends a single comma-separated list, "v1,<mac-A>[,v1,<mac-B>...]".
        // Split on comma; iterate as (scheme, value) pairs. Any v1 entry whose MAC
        // matches the expected one counts as verified. Tolerate the variant where a
        // single token bundles "v1 <b64>" (space-separated scheme+value).
        String[] tokens = signatureHeader.split(",");
        for (int i = 0; i < tokens.length; i++) {
            String trimmed = tokens[i].trim();
            if (trimmed.isEmpty()) continue;
            String candidate;
            if (trimmed.equals("v1")) {
                // Scheme tag in pair form — next token is the b64 MAC.
                if (i + 1 >= tokens.length) break;
                candidate = tokens[++i].trim();
            } else if (trimmed.startsWith("v1 ")) {
                // Single-token form: "v1 <b64>".
                candidate = trimmed.substring(3).trim();
            } else {
                // Bare MAC token (some clients omit the scheme entirely).
                candidate = trimmed;
            }
            if (candidate.isEmpty()) continue;
            byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(candidateBytes, expectedBytes)) {
                return new VerificationResult.Verified();
            }
        }
        return new VerificationResult.Invalid("signature-mismatch");
    }

    /** SHA-256 digest, or {@code null} if the algorithm is somehow unavailable (never on a JRE). */
    @Nullable
    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    @Nullable
    private static byte[] extractHmacKey(byte[] secret) {
        String secretStr = new String(secret, StandardCharsets.UTF_8);
        if (!secretStr.startsWith(WHSEC_PREFIX)) {
            // Non-whsec secret in whsec path; configuration mismatch.
            return null;
        }
        String b64 = secretStr.substring(WHSEC_PREFIX.length());
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static byte[] computeHmac(byte[] key, String msgId, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            mac.update((msgId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return null;
        }
    }

    /**
     * Lowercase-keyed copy. HTTP headers are case-insensitive but {@link WebhookRequest}
     * gives us whatever map the controller built; normalize once at the top.
     */
    private static Map<String, String> normalizeHeaders(Map<String, String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() != null) {
                out.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue());
            }
        }
        return out;
    }
}
