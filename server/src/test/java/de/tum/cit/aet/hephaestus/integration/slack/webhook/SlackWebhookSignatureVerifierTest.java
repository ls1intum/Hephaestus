package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier.WebhookRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("SlackWebhookSignatureVerifier")
class SlackWebhookSignatureVerifierTest extends BaseUnitTest {

    private static final String SECRET = "test-slack-signing-secret-32-chars";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlackWebhookSignatureVerifier defaultVerifier() {
        return new SlackWebhookSignatureVerifier(objectMapper, new SlackWebhookSecretSource(SECRET));
    }

    private SlackWebhookSignatureVerifier verifierWithoutSecret() {
        return new SlackWebhookSignatureVerifier(objectMapper, new SlackWebhookSecretSource(""));
    }

    private static String sign(String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] tag = mac.doFinal(("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
            return "v0=" + HexFormat.of().formatHex(tag);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void urlVerificationEchoesChallenge() {
        byte[] body = "{\"type\":\"url_verification\",\"challenge\":\"abc123\"}".getBytes(StandardCharsets.UTF_8);
        WebhookRequest req = new WebhookRequest(body, Map.of());

        VerificationResult result = defaultVerifier().verify(req);

        assertThat(result).isInstanceOf(VerificationResult.RespondImmediately.class);
        VerificationResult.RespondImmediately r = (VerificationResult.RespondImmediately) result;
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.contentType()).isEqualTo("text/plain");
        assertThat(new String(r.body(), StandardCharsets.UTF_8)).isEqualTo("abc123");
    }

    @Test
    void validSignatureWithFreshTimestampVerifies() {
        String body = "{\"type\":\"event_callback\",\"event\":{\"type\":\"message\"}}";
        String ts = Long.toString(Instant.now().getEpochSecond());
        Map<String, String> headers = Map.of("X-Slack-Signature", sign(ts, body), "X-Slack-Request-Timestamp", ts);
        WebhookRequest req = new WebhookRequest(body.getBytes(StandardCharsets.UTF_8), headers);

        assertThat(defaultVerifier().verify(req)).isInstanceOf(VerificationResult.Verified.class);
    }

    @Test
    void staleTimestampReportsDrift() {
        String body = "{\"type\":\"event_callback\"}";
        long now = Instant.now().getEpochSecond();
        long staleTs = now - 400; // > 300s window
        String tsStr = Long.toString(staleTs);
        Map<String, String> headers = Map.of(
            "X-Slack-Signature",
            sign(tsStr, body),
            "X-Slack-Request-Timestamp",
            tsStr
        );
        WebhookRequest req = new WebhookRequest(body.getBytes(StandardCharsets.UTF_8), headers);

        VerificationResult result = defaultVerifier().verify(req);

        assertThat(result).isInstanceOf(VerificationResult.StaleTimestamp.class);
        VerificationResult.StaleTimestamp stale = (VerificationResult.StaleTimestamp) result;
        assertThat(stale.driftSeconds()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void badSignatureIsInvalid() {
        String body = "{\"type\":\"event_callback\"}";
        String ts = Long.toString(Instant.now().getEpochSecond());
        Map<String, String> headers = Map.of("X-Slack-Signature", "v0=deadbeef", "X-Slack-Request-Timestamp", ts);
        WebhookRequest req = new WebhookRequest(body.getBytes(StandardCharsets.UTF_8), headers);

        VerificationResult result = defaultVerifier().verify(req);

        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).contains("mismatch");
    }

    @Test
    void missingHeadersAreReportedAsMissingSignature() {
        WebhookRequest req = new WebhookRequest(
            "{\"type\":\"event_callback\"}".getBytes(StandardCharsets.UTF_8),
            Map.of()
        );
        assertThat(defaultVerifier().verify(req)).isInstanceOf(VerificationResult.MissingSignature.class);
    }

    @Test
    void missingSecretIsInvalidWithUnconfiguredMessage() {
        String body = "{\"type\":\"event_callback\"}";
        String ts = Long.toString(Instant.now().getEpochSecond());
        Map<String, String> headers = Map.of("X-Slack-Signature", sign(ts, body), "X-Slack-Request-Timestamp", ts);
        WebhookRequest req = new WebhookRequest(body.getBytes(StandardCharsets.UTF_8), headers);

        VerificationResult result = verifierWithoutSecret().verify(req);

        assertThat(result).isInstanceOf(VerificationResult.Invalid.class);
        assertThat(((VerificationResult.Invalid) result).reason()).contains("unconfigured");
    }

    @Test
    void headerLookupIsCaseInsensitive() {
        // Servlet containers may normalize header case; verifier must tolerate both.
        String body = "{\"type\":\"event_callback\"}";
        String ts = Long.toString(Instant.now().getEpochSecond());
        Map<String, String> headers = Map.of("x-slack-signature", sign(ts, body), "x-slack-request-timestamp", ts);
        WebhookRequest req = new WebhookRequest(body.getBytes(StandardCharsets.UTF_8), headers);

        assertThat(defaultVerifier().verify(req)).isInstanceOf(VerificationResult.Verified.class);
    }
}
