package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SlackSignatureVerifierTest extends BaseUnitTest {

    private static final String SECRET = "8f742231b10e4f81b3e2c9a7d6541abc";
    private static final long NOW = 1_700_000_000L;

    @Test
    void validSignatureWithinWindowVerifies() {
        byte[] body = body("token=abc&command=%2Fmentor");
        String ts = Long.toString(NOW);
        String sig = sign(SECRET, ts, body);

        assertThat(newVerifier(SECRET).verify(ts, sig, body, NOW)).isTrue();
    }

    @Test
    void expiredTimestampOutsideReplayWindowRejected() {
        byte[] body = body("token=abc&command=%2Fmentor");
        String ts = Long.toString(NOW - 301);
        String sig = sign(SECRET, ts, body);

        assertThat(newVerifier(SECRET).verify(ts, sig, body, NOW)).isFalse();
    }

    @Test
    void timestampAtWindowEdgeAccepted() {
        byte[] body = body("{}");
        String ts = Long.toString(NOW - 300);
        String sig = sign(SECRET, ts, body);

        assertThat(newVerifier(SECRET).verify(ts, sig, body, NOW)).isTrue();
    }

    @Test
    void unconfiguredSecretFailsFast() {
        assertThatThrownBy(() -> newVerifier("   "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("signing-secret is blank");
    }

    @Test
    @DisplayName("server role can enable Slack without the webhook-only signing secret")
    void serverRoleDoesNotCreateVerifierOrRequireSigningSecret() {
        new ApplicationContextRunner()
            .withUserConfiguration(SlackSignatureVerifier.class)
            .withPropertyValues("hephaestus.integration.slack.enabled=true", "hephaestus.runtime.webhook.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(SlackSignatureVerifier.class));
    }

    @Test
    void tamperedBodyRejected() {
        byte[] signedBody = body("{\"text\":\"hello\"}");
        String ts = Long.toString(NOW);
        String sig = sign(SECRET, ts, signedBody);
        byte[] tampered = body("{\"text\":\"HELLO\"}");

        assertThat(newVerifier(SECRET).verify(ts, sig, tampered, NOW)).isFalse();
    }

    @Test
    void wrongSignatureRejected() {
        byte[] body = body("{}");
        String ts = Long.toString(NOW);

        assertThat(newVerifier(SECRET).verify(ts, "v0=deadbeef", body, NOW)).isFalse();
    }

    @Test
    void nonNumericTimestampRejectedNotThrown() {
        byte[] body = body("{}");
        String sig = sign(SECRET, "not-a-number", body);

        assertThat(newVerifier(SECRET).verify("not-a-number", sig, body, NOW)).isFalse();
    }

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

    private static SlackSignatureVerifier newVerifier(String secret) {
        return new SlackSignatureVerifier(secret);
    }

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
