package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Unit tests pinning the inbound-Slack auth boundary: HMAC-SHA256 over {@code "v0:{timestamp}:{rawBody}"} keyed by
 * the app signing secret, compared constant-time to {@code X-Slack-Signature}, with a 300&nbsp;s replay window on
 * {@code X-Slack-Request-Timestamp} (Slack's documented scheme). Each test names the mutant it kills. Booleans only —
 * the verifier owns no collaborators.
 */
class SlackSignatureVerifierTest extends BaseUnitTest {

    private static final String SECRET = "8f742231b10e4f81b3e2c9a7d6541abc";
    private static final long NOW = 1_700_000_000L;

    /** (a) A correctly v0-signed request inside the window PASSES — kills "always reject" / broken-HMAC mutants. */
    @Test
    void validSignatureWithinWindowVerifies() {
        byte[] body = body("token=abc&command=%2Fmentor");
        String ts = Long.toString(NOW);
        String sig = sign(SECRET, ts, body);

        assertThat(newVerifier(SECRET).verify(ts, sig, body, NOW)).isTrue();
    }

    /**
     * (b) The SAME signed payload replayed at now-301s is REJECTED — the headline replay control; kills a mutant
     * that widens/removes the {@code MAX_SKEW_SECONDS} window (e.g. {@code >} → {@code >=}, or dropping the check).
     */
    @Test
    void expiredTimestampOutsideReplayWindowRejected() {
        byte[] body = body("token=abc&command=%2Fmentor");
        String ts = Long.toString(NOW - 301);
        // Signature is itself valid for that timestamp; only the age must fail it.
        String sig = sign(SECRET, ts, body);

        assertThat(newVerifier(SECRET).verify(ts, sig, body, NOW)).isFalse();
    }

    /** A timestamp exactly 300s old is still INSIDE the window (boundary) — kills a {@code >} → {@code >=} mutant. */
    @Test
    void timestampAtWindowEdgeAccepted() {
        byte[] body = body("{}");
        String ts = Long.toString(NOW - 300);
        String sig = sign(SECRET, ts, body);

        assertThat(newVerifier(SECRET).verify(ts, sig, body, NOW)).isTrue();
    }

    /** (c) A blank signing secret REJECTS an otherwise-valid signature — kills a mutant that drops {@code !configured}. */
    @Test
    void unconfiguredSecretRejectsEvenAValidLookingSignature() {
        byte[] body = body("{}");
        String ts = Long.toString(NOW);
        // Sign with the real secret, then verify against an unconfigured verifier — must still reject.
        String sig = sign(SECRET, ts, body);

        SlackSignatureVerifier verifier = newVerifier("   ");
        assertThat(verifier.isConfigured()).isFalse();
        assertThat(verifier.verify(ts, sig, body, NOW)).isFalse();
    }

    /** (d) A tampered body no longer matches the signature — kills a mutant that skips the constant-time compare. */
    @Test
    void tamperedBodyRejected() {
        byte[] signedBody = body("{\"text\":\"hello\"}");
        String ts = Long.toString(NOW);
        String sig = sign(SECRET, ts, signedBody);
        byte[] tampered = body("{\"text\":\"HELLO\"}");

        assertThat(newVerifier(SECRET).verify(ts, sig, tampered, NOW)).isFalse();
    }

    /** (d') A wrong/garbage signature is REJECTED. */
    @Test
    void wrongSignatureRejected() {
        byte[] body = body("{}");
        String ts = Long.toString(NOW);

        assertThat(newVerifier(SECRET).verify(ts, "v0=deadbeef", body, NOW)).isFalse();
    }

    /**
     * (e) A non-numeric timestamp is REJECTED, not thrown — kills a mutant that lets {@code Long.parseLong} escape as
     * a NumberFormatException (which would 500 instead of 401).
     */
    @Test
    void nonNumericTimestampRejectedNotThrown() {
        byte[] body = body("{}");
        String sig = sign(SECRET, "not-a-number", body);

        assertThat(newVerifier(SECRET).verify("not-a-number", sig, body, NOW)).isFalse();
    }

    /** (e') A null timestamp / null signature is REJECTED without an NPE. */
    @Test
    void nullTimestampOrSignatureRejected() {
        byte[] body = body("{}");
        String ts = Long.toString(NOW);
        String sig = sign(SECRET, ts, body);

        SlackSignatureVerifier verifier = newVerifier(SECRET);
        assertThat(verifier.verify(null, sig, body, NOW)).isFalse();
        assertThat(verifier.verify(ts, null, body, NOW)).isFalse();
        assertThat(verifier.verify(ts, sig, null, NOW)).isFalse();
    }

    // Helpers

    private static SlackSignatureVerifier newVerifier(String secret) {
        return new SlackSignatureVerifier(secret);
    }

    /** Reproduces Slack's v0 scheme independently of the class under test, so the test pins the wire format. */
    private static String sign(String secret, String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
            return "v0=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] body(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
