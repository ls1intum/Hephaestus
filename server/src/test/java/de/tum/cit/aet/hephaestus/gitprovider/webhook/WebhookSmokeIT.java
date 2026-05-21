package de.tum.cit.aet.hephaestus.gitprovider.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.github.GitHubWebhookController;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.web.GitLabWebhookController;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires-it-up smoke for the receiver. Uses {@code MockMvc.standaloneSetup} (no Spring context) +
 * a recording publisher to assert {@link PublishRequest} construction end-to-end, including the
 * critical byte-equality of the raw request body through Spring's {@code byte[]} binding.
 */
@Tag("integration")
class WebhookSmokeIT {

    private static final String SECRET = "test-secret-test-secret-test-sec";

    private final WebhookProperties properties = new WebhookProperties(
        "https://example.test",
        SECRET,
        new WebhookProperties.TokenRotation(7, 90),
        new WebhookProperties.Publish(Duration.ofSeconds(9), 5, Duration.ofMillis(200)),
        new WebhookProperties.Stream(Duration.ofMinutes(2), Duration.ofDays(180), 2_000_000L),
        new WebhookProperties.Shutdown(Duration.ofSeconds(15)),
        new WebhookProperties.Http(26_214_400L)
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeterRegistry meters = new SimpleMeterRegistry();
    private final RecordingJetStreamPublisher recorder = new RecordingJetStreamPublisher(meters);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
        new GitHubWebhookController(properties, objectMapper, recorder, meters),
        new GitLabWebhookController(properties, objectMapper, recorder, meters)
    ).build();

    @BeforeEach
    void clearRecorder() {
        recorder.requests.clear();
    }

    @Test
    void gitlabPushPublishesByteEqualBody() throws Exception {
        byte[] body = Files.readAllBytes(Paths.get("src/test/resources/gitlab/push.json"));

        mockMvc
            .perform(
                post("/gitlab")
                    .header("X-Gitlab-Token", SECRET)
                    .header("X-Gitlab-Event", "Push Hook")
                    .header("X-Gitlab-Event-UUID", "abc-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk());

        assertThat(recorder.requests).hasSize(1);
        PublishRequest captured = recorder.requests.get(0);
        assertThat(captured.subject()).startsWith("gitlab.").endsWith(".push");
        assertThat(captured.dedupId()).isEqualTo("gitlab-abc-123");
        assertThat(captured.headers()).containsEntry("Nats-Msg-Id", "gitlab-abc-123");
        assertThat(captured.body()).isEqualTo(body);
    }

    @Test
    void gitlabRejectsInvalidToken() throws Exception {
        byte[] body = Files.readAllBytes(Paths.get("src/test/resources/gitlab/push.json"));

        mockMvc
            .perform(
                post("/gitlab")
                    .header("X-Gitlab-Token", "wrong-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isUnauthorized());

        assertThat(recorder.requests).isEmpty();
    }

    @Test
    void githubPushPublishesByteEqualBody() throws Exception {
        byte[] body = Files.readAllBytes(Paths.get("src/test/resources/github/push.json"));
        String signature = "sha256=" + sha256Hex(body);

        mockMvc
            .perform(
                post("/github")
                    .header("X-Hub-Signature-256", signature)
                    .header("X-GitHub-Event", "push")
                    .header("X-GitHub-Delivery", "delivery-id-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk());

        assertThat(recorder.requests).hasSize(1);
        PublishRequest captured = recorder.requests.get(0);
        assertThat(captured.subject()).startsWith("github.").endsWith(".push");
        assertThat(captured.dedupId()).isEqualTo("github-delivery-id-1");
        assertThat(captured.headers())
            .containsEntry("Nats-Msg-Id", "github-delivery-id-1")
            .containsEntry("X-GitHub-Event", "push")
            .containsEntry("X-GitHub-Delivery", "delivery-id-1")
            .doesNotContainKey("X-GitHub-Action"); // consumers read action from payload body
        assertThat(captured.body()).isEqualTo(body);
    }

    @Test
    void githubRejectsInvalidSignature() throws Exception {
        byte[] body = Files.readAllBytes(Paths.get("src/test/resources/github/push.json"));

        mockMvc
            .perform(
                post("/github")
                    .header("X-Hub-Signature-256", "sha256=invalid")
                    .header("X-GitHub-Event", "push")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isUnauthorized());

        assertThat(recorder.requests).isEmpty();
    }

    @Test
    void githubPingShortCircuitsWithoutPublish() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + sha256Hex(body);

        mockMvc
            .perform(
                post("/github")
                    .header("X-Hub-Signature-256", signature)
                    .header("X-GitHub-Event", "ping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk());

        assertThat(recorder.requests).isEmpty();
    }

    @Test
    void githubReturns400OnMalformedJson() throws Exception {
        byte[] body = "{not-json".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + sha256Hex(body);

        mockMvc
            .perform(
                post("/github")
                    .header("X-Hub-Signature-256", signature)
                    .header("X-GitHub-Event", "push")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest());

        assertThat(recorder.requests).isEmpty();
    }

    @Test
    void githubReturns400OnMissingEventHeader() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + sha256Hex(body);

        mockMvc
            .perform(
                post("/github")
                    .header("X-Hub-Signature-256", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest());

        assertThat(recorder.requests).isEmpty();
    }

    @Test
    void gitlabReturns503OnPublishFailure() throws Exception {
        byte[] body = Files.readAllBytes(Paths.get("src/test/resources/gitlab/push.json"));
        ObjectMapper sharedMapper = new ObjectMapper();
        WebhookProperties props = properties;
        JetStreamPublisher failing = new JetStreamPublisher(
            null,
            Retry.of("test-noop", RetryConfig.custom().maxAttempts(1).build()),
            props,
            new SimpleMeterRegistry()
        ) {
            @Override
            public void publish(PublishRequest request) {
                throw new PublishFailedException("simulated NATS outage", new RuntimeException("boom"));
            }
        };
        MockMvc localMvc = MockMvcBuilders.standaloneSetup(
            new GitLabWebhookController(props, sharedMapper, failing, meters)
        ).build();

        localMvc
            .perform(
                post("/gitlab")
                    .header("X-Gitlab-Token", SECRET)
                    .header("X-Gitlab-Event", "Push Hook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void gitlabReturns401WhenServerHasNoSecretConfigured() throws Exception {
        WebhookProperties unsecured = new WebhookProperties(
            null,
            "", // no secret configured
            new WebhookProperties.TokenRotation(7, 90),
            new WebhookProperties.Publish(java.time.Duration.ofSeconds(9), 5, java.time.Duration.ofMillis(200)),
            new WebhookProperties.Stream(java.time.Duration.ofMinutes(2), java.time.Duration.ofDays(180), 2_000_000L),
            new WebhookProperties.Shutdown(java.time.Duration.ofSeconds(15)),
            new WebhookProperties.Http(26_214_400L)
        );
        MockMvc unsecuredMvc = MockMvcBuilders.standaloneSetup(
            new GitLabWebhookController(unsecured, objectMapper, recorder, meters)
        ).build();

        unsecuredMvc
            .perform(
                post("/gitlab")
                    .header("X-Gitlab-Token", "anything")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnauthorized());

        assertThat(recorder.requests).isEmpty();
    }

    private static String sha256Hex(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    static class RecordingJetStreamPublisher extends JetStreamPublisher {

        final List<PublishRequest> requests = new ArrayList<>();

        RecordingJetStreamPublisher(MeterRegistry registry) {
            super(null, Retry.of("test-noop", RetryConfig.custom().maxAttempts(1).build()), null, registry);
        }

        @Override
        public void publish(PublishRequest request) {
            requests.add(request);
        }
    }
}
