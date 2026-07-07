package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSignatureVerifier;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interactivity-controller unit tests: the HMAC is computed IN-TEST (real {@link SlackSignatureVerifier} with a
 * known secret) so a genuinely signed payload ACKs 200 and dispatches, while a bad signature is rejected 401
 * before any handler runs. A same-thread executor makes the post-ACK dispatch deterministic.
 */
class SlackInteractivityControllerTest extends BaseUnitTest {

    private static final String SIGNING_SECRET = "test-signing-secret";

    @Mock
    private SlackFeedbackHandler handler;

    private SlackInteractivityController controller;

    @BeforeEach
    void setUp() {
        controller = new SlackInteractivityController(
            new SlackSignatureVerifier(SIGNING_SECRET),
            handler,
            JsonMapper.builder().build(),
            Runnable::run // same-thread: dispatch happens synchronously within the request for the assertion
        );
    }

    private static String formBody(String payloadJson) {
        return "payload=" + URLEncoder.encode(payloadJson, StandardCharsets.UTF_8);
    }

    private static String sign(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
            byte[] digest = mac.doFinal(body);
            return "v0=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void signedBlockActions_acks200_andDispatches() {
        byte[] body = formBody("{\"type\":\"block_actions\",\"actions\":[]}").getBytes(StandardCharsets.UTF_8);
        String ts = Long.toString(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", ts);
        headers.add("X-Slack-Signature", sign(ts, body));

        ResponseEntity<String> res = controller.interactivity(body, headers);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(handler).handleBlockActions(any());
    }

    @Test
    void badSignature_rejected401_noDispatch() {
        byte[] body = formBody("{\"type\":\"block_actions\",\"actions\":[]}").getBytes(StandardCharsets.UTF_8);
        String ts = Long.toString(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", ts);
        headers.add("X-Slack-Signature", "v0=deadbeef");

        ResponseEntity<String> res = controller.interactivity(body, headers);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(handler);
    }

    @Test
    void saturatedExecutor_stillAcks200_soSlackDoesNotSeeAnErrorDialogOrRetry() {
        // A saturated pool throws RejectedExecutionException from execute(); the controller must swallow it and
        // still ACK 200 — a propagated rejection would 500 (error dialog + Slack retry).
        Executor rejecting = task -> {
            throw new RejectedExecutionException("pool full");
        };
        SlackInteractivityController saturated = new SlackInteractivityController(
            new SlackSignatureVerifier(SIGNING_SECRET),
            handler,
            JsonMapper.builder().build(),
            rejecting
        );
        byte[] body = formBody("{\"type\":\"block_actions\",\"actions\":[]}").getBytes(StandardCharsets.UTF_8);
        String ts = Long.toString(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Slack-Request-Timestamp", ts);
        headers.add("X-Slack-Signature", sign(ts, body));

        ResponseEntity<String> res = saturated.interactivity(body, headers);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(handler); // the task never ran, but the ACK still went out
    }
}
