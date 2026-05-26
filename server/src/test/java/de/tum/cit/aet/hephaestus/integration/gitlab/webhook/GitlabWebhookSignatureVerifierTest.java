package de.tum.cit.aet.hephaestus.integration.gitlab.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.WebhookRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the dual-mode GitLab webhook signature verifier.
 *
 * <p>The two paths (legacy plaintext token vs. Standard Webhooks HMAC) share an outer
 * dispatcher, so each path is exercised in isolation plus a final "both headers"
 * priority test that nails down the safer-mode-wins rule.
 */
@DisplayName("GitlabWebhookSignatureVerifier dual-mode behaviour")
class GitlabWebhookSignatureVerifierTest extends BaseUnitTest {

    private static final String PLAINTEXT_SECRET = "shared-gitlab-secret-32-bytes-long-XYZ";
    private static final byte[] WHSEC_KEY = "raw-hmac-key-material-32-bytes-_____XYZ".getBytes(StandardCharsets.UTF_8);

    /** Frozen test clock — keeps timestamp drift math deterministic. */
    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    // ── Plaintext path ─────────────────────────────────────────────────────

    @Test
    void plaintextMatchingTokenVerified() {
        var verifier = newVerifier(PLAINTEXT_SECRET);
        var request = req(body("{}"), header("X-Gitlab-Token", PLAINTEXT_SECRET));

        assertThat(verifier.verify(request)).isInstanceOf(VerificationResult.Verified.class);
    }

    @Test
    void plaintextMismatchedTokenInvalid() {
        var verifier = newVerifier(PLAINTEXT_SECRET);
        var request = req(body("{}"), header("X-Gitlab-Token", "not-the-secret"));

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("token-mismatch");
    }

    @Test
    void plaintextMissingTokenAndSignatureReturnsMissingSignature() {
        var verifier = newVerifier(PLAINTEXT_SECRET);
        var request = req(body("{}"));

        assertThat(verifier.verify(request)).isInstanceOf(VerificationResult.MissingSignature.class);
    }

    @Test
    void plaintextSecretAbsentSurfacesAsInvalid() {
        // Empty configured secret → source returns Optional.empty(); verifier reports Invalid
        // (NOT MissingSignature, because the *request* carries a token).
        var verifier = newVerifierWithSource(emptySource());
        var request = req(body("{}"), header("X-Gitlab-Token", PLAINTEXT_SECRET));

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("missing-secret");
    }

    // ── whsec_* HMAC path ──────────────────────────────────────────────────

    @Test
    void whsecMatchingMacVerified() {
        byte[] body = body("{\"object_kind\":\"push\"}");
        String msgId = "msg_2v9D0aH9zN6";
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String mac = computeBase64Mac(WHSEC_KEY, msgId, timestamp, body);

        var verifier = newVerifierWhsec();
        var request = req(
            body,
            header("webhook-id", msgId),
            header("webhook-timestamp", timestamp),
            header("X-Gitlab-Signature", "v1," + mac)
        );

        assertThat(verifier.verify(request)).isInstanceOf(VerificationResult.Verified.class);
    }

    @Test
    void whsecMultipleSignaturesAnyMatchWins() {
        byte[] body = body("{}");
        String msgId = "msg_x";
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String validMac = computeBase64Mac(WHSEC_KEY, msgId, timestamp, body);
        String otherMac = computeBase64Mac("different-key".getBytes(StandardCharsets.UTF_8), msgId, timestamp, body);

        var verifier = newVerifierWhsec();
        var request = req(
            body,
            header("webhook-id", msgId),
            header("webhook-timestamp", timestamp),
            // Key rotation case: old + new sig sent in one header.
            header("X-Gitlab-Signature", "v1," + otherMac + ",v1," + validMac)
        );

        assertThat(verifier.verify(request)).isInstanceOf(VerificationResult.Verified.class);
    }

    @Test
    void whsecTamperedBodyInvalid() {
        byte[] originalBody = body("{\"object_kind\":\"push\"}");
        byte[] tamperedBody = body("{\"object_kind\":\"PUSH\"}");
        String msgId = "msg_x";
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String mac = computeBase64Mac(WHSEC_KEY, msgId, timestamp, originalBody);

        var verifier = newVerifierWhsec();
        var request = req(
            tamperedBody,
            header("webhook-id", msgId),
            header("webhook-timestamp", timestamp),
            header("X-Gitlab-Signature", "v1," + mac)
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("signature-mismatch");
    }

    @Test
    void whsecStaleTimestampSurfacesDrift() {
        byte[] body = body("{}");
        long sixMinutesAgo = NOW.minusSeconds(6 * 60).getEpochSecond();
        String msgId = "msg_x";
        String timestamp = String.valueOf(sixMinutesAgo);
        String mac = computeBase64Mac(WHSEC_KEY, msgId, timestamp, body);

        var verifier = newVerifierWhsec();
        var request = req(
            body,
            header("webhook-id", msgId),
            header("webhook-timestamp", timestamp),
            header("X-Gitlab-Signature", "v1," + mac)
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.StaleTimestamp.class);
        assertThat(((VerificationResult.StaleTimestamp) result).driftSeconds()).isEqualTo(6 * 60);
    }

    @Test
    void whsecFutureTimestampAlsoStale() {
        // Symmetric clock-skew window: future-dated requests also fail.
        byte[] body = body("{}");
        long sixMinutesAhead = NOW.plusSeconds(6 * 60).getEpochSecond();
        String msgId = "msg_x";
        String timestamp = String.valueOf(sixMinutesAhead);
        String mac = computeBase64Mac(WHSEC_KEY, msgId, timestamp, body);

        var verifier = newVerifierWhsec();
        var request = req(
            body,
            header("webhook-id", msgId),
            header("webhook-timestamp", timestamp),
            header("X-Gitlab-Signature", "v1," + mac)
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.StaleTimestamp.class);
        assertThat(((VerificationResult.StaleTimestamp) result).driftSeconds()).isEqualTo(6 * 60);
    }

    @Test
    void whsecMissingWebhookIdInvalid() {
        var verifier = newVerifierWhsec();
        var request = req(
            body("{}"),
            header("webhook-timestamp", String.valueOf(NOW.getEpochSecond())),
            header("X-Gitlab-Signature", "v1,deadbeef")
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("missing-webhook-id");
    }

    @Test
    void whsecMissingWebhookTimestampInvalid() {
        var verifier = newVerifierWhsec();
        var request = req(body("{}"), header("webhook-id", "msg_x"), header("X-Gitlab-Signature", "v1,deadbeef"));

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("missing-webhook-timestamp");
    }

    @Test
    void whsecMalformedTimestampInvalid() {
        var verifier = newVerifierWhsec();
        var request = req(
            body("{}"),
            header("webhook-id", "msg_x"),
            header("webhook-timestamp", "not-a-number"),
            header("X-Gitlab-Signature", "v1,deadbeef")
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("malformed-webhook-timestamp");
    }

    @Test
    void whsecMalformedSecretInvalid() {
        // Secret missing whsec_ prefix — signature header present so we attempt the modern
        // path; secret extraction fails; result is Invalid, not MissingSignature.
        WebhookSecretSource source = staticSource("not-a-whsec-secret".getBytes(StandardCharsets.UTF_8));
        var verifier = new GitlabWebhookSignatureVerifier(source, CLOCK);
        var request = req(
            body("{}"),
            header("webhook-id", "msg_x"),
            header("webhook-timestamp", String.valueOf(NOW.getEpochSecond())),
            header("X-Gitlab-Signature", "v1,deadbeef")
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("malformed-whsec-secret");
    }

    // ── Dual-mode priority ────────────────────────────────────────────────

    @Test
    void bothHeadersPresentSignatureWins() {
        byte[] body = body("{}");
        String msgId = "msg_x";
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String mac = computeBase64Mac(WHSEC_KEY, msgId, timestamp, body);

        var verifier = newVerifierWhsec();
        var request = req(
            body,
            // Both modes' headers present. The plaintext token is wrong; the signature is correct.
            // Verifier MUST pick the modern path and verify; the wrong plaintext token must not
            // cause a fallback or a downgrade.
            header("X-Gitlab-Token", "WRONG-plaintext"),
            header("webhook-id", msgId),
            header("webhook-timestamp", timestamp),
            header("X-Gitlab-Signature", "v1," + mac)
        );

        assertThat(verifier.verify(request)).isInstanceOf(VerificationResult.Verified.class);
    }

    @Test
    void bothHeadersPresentBadSignatureDoesNotFallback() {
        // Opposite case: signature header present but invalid; plaintext token is correct.
        // We MUST NOT fall back — that would be a downgrade primitive.
        byte[] body = body("{}");
        var verifier = newVerifierWhsec();
        var request = req(
            body,
            header("X-Gitlab-Token", PLAINTEXT_SECRET), // would be correct for the plaintext source
            header("webhook-id", "msg_x"),
            header("webhook-timestamp", String.valueOf(NOW.getEpochSecond())),
            header("X-Gitlab-Signature", "v1,definitely-not-a-real-mac")
        );

        VerificationResult result = verifier.verify(request);
        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).isEqualTo("signature-mismatch");
    }

    @Test
    void verifierIdentifiesAsGitlabKind() {
        assertThat(newVerifier(PLAINTEXT_SECRET).kind()).isEqualTo(IntegrationKind.GITLAB);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static GitlabWebhookSignatureVerifier newVerifier(String secret) {
        return new GitlabWebhookSignatureVerifier(staticSource(secret.getBytes(StandardCharsets.UTF_8)), CLOCK);
    }

    /** Returns a verifier whose source serves a {@code whsec_<base64>} secret. */
    private static GitlabWebhookSignatureVerifier newVerifierWhsec() {
        String whsec = "whsec_" + Base64.getEncoder().encodeToString(WHSEC_KEY);
        return new GitlabWebhookSignatureVerifier(staticSource(whsec.getBytes(StandardCharsets.UTF_8)), CLOCK);
    }

    private static GitlabWebhookSignatureVerifier newVerifierWithSource(WebhookSecretSource source) {
        return new GitlabWebhookSignatureVerifier(source, CLOCK);
    }

    private static WebhookSecretSource staticSource(byte[] secretBytes) {
        return new WebhookSecretSource() {
            @Override
            public IntegrationKind kind() {
                return IntegrationKind.GITLAB;
            }

            @Override
            public Scope scope() {
                return Scope.APP_GLOBAL;
            }

            @Override
            public Optional<byte[]> getSecret(SecretLookup lookup) {
                return Optional.of(secretBytes.clone());
            }
        };
    }

    private static WebhookSecretSource emptySource() {
        return new WebhookSecretSource() {
            @Override
            public IntegrationKind kind() {
                return IntegrationKind.GITLAB;
            }

            @Override
            public Scope scope() {
                return Scope.APP_GLOBAL;
            }

            @Override
            public Optional<byte[]> getSecret(SecretLookup lookup) {
                return Optional.empty();
            }
        };
    }

    private static byte[] body(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Map.Entry<String, String> header(String name, String value) {
        return Map.entry(name, value);
    }

    @SafeVarargs
    private static WebhookRequest req(byte[] body, Map.Entry<String, String>... entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (var e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return new WebhookRequest(body, map);
    }

    /** Reference HMAC computation matching the verifier's "<msgId>.<timestamp>.<body>" form. */
    private static String computeBase64Mac(byte[] key, String msgId, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((msgId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
