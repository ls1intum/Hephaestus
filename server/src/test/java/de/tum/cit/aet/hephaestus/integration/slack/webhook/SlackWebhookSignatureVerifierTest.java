package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier.WebhookRequest;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSignatureVerifier;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SlackWebhookSignatureVerifierTest extends BaseUnitTest {

    private static final String SECRET = "test-signing-secret";

    private final SlackWebhookSignatureVerifier verifier = new SlackWebhookSignatureVerifier(
        new SlackSignatureVerifier(SECRET),
        JsonMapper.builder().build()
    );

    @Test
    void urlVerificationRespondsWithPlainChallenge() {
        byte[] body = "{\"type\":\"url_verification\",\"challenge\":\"abc123\"}".getBytes(StandardCharsets.UTF_8);

        VerificationResult result = verifier.verify(
            new WebhookRequest(body, signedHeaders(body, Instant.now().getEpochSecond()))
        );

        assertThat(result).isInstanceOf(VerificationResult.RespondImmediately.class);
        VerificationResult.RespondImmediately response = (VerificationResult.RespondImmediately) result;
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("text/plain; charset=utf-8");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("abc123");
    }

    @Test
    void validEventCallbackIsVerified() {
        byte[] body = "{\"type\":\"event_callback\",\"event_id\":\"Ev1\",\"event\":{\"type\":\"message\"}}".getBytes(
            StandardCharsets.UTF_8
        );

        VerificationResult result = verifier.verify(
            new WebhookRequest(body, signedHeaders(body, Instant.now().getEpochSecond()))
        );

        assertThat(result).isInstanceOf(VerificationResult.Verified.class);
    }

    private static Map<String, String> signedHeaders(byte[] body, long timestamp) {
        return Map.of(
            "X-Slack-Request-Timestamp",
            String.valueOf(timestamp),
            "X-Slack-Signature",
            signature(body, timestamp)
        );
    }

    private static String signature(byte[] body, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
            return "v0=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
