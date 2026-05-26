package de.tum.cit.aet.hephaestus.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import tools.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.integration.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.webhook.PublishRequest;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GithubSubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GithubWebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GithubWebhookSignatureVerifier;
import de.tum.cit.aet.hephaestus.integration.gitlab.webhook.GitlabSubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.gitlab.webhook.GitlabWebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.gitlab.webhook.GitlabWebhookSignatureVerifier;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Pins the unified {@code /webhooks/{kind}} pipeline contract for GitHub and GitLab.
 *
 * <p>Confirms:
 * <ul>
 *   <li>a verified GitHub webhook produces the expected NATS subject + dedup-id;</li>
 *   <li>a verified GitLab webhook does the same; and</li>
 *   <li>the GitHub {@code ping} setup event still short-circuits to 200 OK without
 *       publishing to NATS (moved from the deleted controller into
 *       {@link GithubWebhookSignatureVerifier#verify} as a
 *       {@link de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.VerificationResult.RespondImmediately
 *       RespondImmediately}).</li>
 * </ul>
 */
@DisplayName("Webhook ingest pipeline — unified /webhooks/{kind}")
class WebhookIngestParityTest extends BaseUnitTest {

    private static final String SHARED_SECRET = "parity-test-shared-secret-32-bytes-long-XYZ";

    @Mock
    private JetStreamPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GitHub webhook: unified pipeline publishes with expected subject + dedup id")
    void githubUnified() {
        byte[] body = ("{\"repository\":{\"owner\":{\"login\":\"acme\"},\"name\":\"hephaestus\"},"
            + "\"action\":\"opened\"}").getBytes(StandardCharsets.UTF_8);
        String sig = "sha256=" + hmacHex(SHARED_SECRET, body);
        String deliveryId = "11112222-3333-4444-5555-666677778888";

        WebhookProperties props = props(SHARED_SECRET);
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(new GithubWebhookSignatureVerifier(new GithubWebhookSecretSource(props))),
            List.of(new GithubSubjectKeyDeriver()),
            publisher,
            objectMapper
        );

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-GitHub-Event", "pull_request");
        headers.put("X-GitHub-Delivery", deliveryId);
        headers.put("X-Hub-Signature-256", sig);
        pipeline.handle(IntegrationKind.GITHUB, body, headers);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publisher, times(1)).publish(captor.capture());
        PublishRequest req = captor.getValue();

        assertThat(req.subject()).isEqualTo("github.acme.hephaestus.pull_request");
        assertThat(req.dedupId()).isEqualTo("github-" + deliveryId);
    }

    @Test
    @DisplayName("GitHub webhook: ping is short-circuited in the verifier (no publish, 200 OK)")
    void githubPingShortCircuits() {
        WebhookProperties props = props(SHARED_SECRET);
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(new GithubWebhookSignatureVerifier(new GithubWebhookSecretSource(props))),
            List.of(new GithubSubjectKeyDeriver()),
            publisher,
            objectMapper
        );

        Map<String, String> pingHeaders = new LinkedHashMap<>();
        pingHeaders.put("X-GitHub-Event", "ping");
        pingHeaders.put("X-GitHub-Delivery", "delivery-id");
        // No signature header — GitHub may post the install-time ping before the secret
        // is wired and the verifier must still return 200. Verifier intercepts ping
        // BEFORE the signature check.
        var resp = pipeline.handle(IntegrationKind.GITHUB, "{}".getBytes(StandardCharsets.UTF_8), pingHeaders);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isInstanceOf(byte[].class);
        assertThat(new String((byte[]) resp.getBody(), StandardCharsets.UTF_8)).contains("pong");
        verify(publisher, times(0)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("GitLab webhook: unified pipeline publishes with expected subject + dedup id")
    void gitlabUnified() {
        byte[] body = ("{\"object_kind\":\"push\","
            + "\"project\":{\"path_with_namespace\":\"ase/ipraktikum/ios2526/introcourse\"}}")
            .getBytes(StandardCharsets.UTF_8);
        String eventUuid = "aaaa1111-bbbb-2222-cccc-333344445555";

        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(new GitlabWebhookSignatureVerifier(List.of(new GitlabWebhookSecretSource(SHARED_SECRET)))),
            List.of(new GitlabSubjectKeyDeriver()),
            publisher,
            objectMapper
        );

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Gitlab-Token", SHARED_SECRET);
        headers.put("X-Gitlab-Event", "Push Hook");
        headers.put("X-Gitlab-Event-UUID", eventUuid);
        pipeline.handle(IntegrationKind.GITLAB, body, headers);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publisher, times(1)).publish(captor.capture());
        PublishRequest req = captor.getValue();

        assertThat(req.subject()).isEqualTo("gitlab.ase~ipraktikum~ios2526.introcourse.push");
        assertThat(req.dedupId()).isEqualTo("gitlab-" + eventUuid);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static WebhookProperties props(String secret) {
        return new WebhookProperties(
            null,
            secret,
            new WebhookProperties.TokenRotation(7, 90),
            new WebhookProperties.Publish(Duration.ofSeconds(9), 5, Duration.ofMillis(200)),
            new WebhookProperties.Stream(Duration.ofMinutes(2), Duration.ofDays(180), 2_000_000L),
            new WebhookProperties.Shutdown(Duration.ofSeconds(15)),
            new WebhookProperties.Http(26_214_400L)
        );
    }

    private static String hmacHex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
