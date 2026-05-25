package de.tum.cit.aet.hephaestus.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import tools.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.PublishRequest;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GitHubWebhookController;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GithubSubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GithubWebhookSecretSource;
import de.tum.cit.aet.hephaestus.integration.github.webhook.GithubWebhookSignatureVerifier;
import de.tum.cit.aet.hephaestus.integration.gitlab.webhook.GitLabWebhookController;
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
 * Parity test for the {@code /github} + {@code /gitlab} URL anchors and the
 * {@link WebhookIngestPipeline}. Confirms that BOTH the legacy controller path
 * AND a direct pipeline invocation emit the same JetStream {@link PublishRequest}
 * — same subject, same dedup-id, same body. Without this guard, the controller
 * shims could drift away from the pipeline silently.
 */
@DisplayName("Webhook ingest parity: legacy controllers ⇄ unified pipeline")
class WebhookIngestParityTest extends BaseUnitTest {

    private static final String SHARED_SECRET = "parity-test-shared-secret-32-bytes-long-XYZ";

    @Mock
    private JetStreamPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GitHub webhook: legacy /github controller + unified pipeline emit identical PublishRequest")
    void githubParity() {
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
        GitHubWebhookController controller = new GitHubWebhookController(pipeline);

        // Path A: through the legacy controller shim.
        controller.receive(body, "pull_request", deliveryId, sig);
        // Path B: directly through the unified pipeline.
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-GitHub-Event", "pull_request");
        headers.put("X-GitHub-Delivery", deliveryId);
        headers.put("X-Hub-Signature-256", sig);
        pipeline.handle(IntegrationKind.GITHUB, body, headers);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publisher, times(2)).publish(captor.capture());
        PublishRequest legacy = captor.getAllValues().get(0);
        PublishRequest unified = captor.getAllValues().get(1);

        assertThat(legacy.subject())
            .as("subject must match the unified pipeline")
            .isEqualTo(unified.subject())
            .isEqualTo("github.acme.hephaestus.pull_request");
        assertThat(legacy.dedupId())
            .as("dedup id derives from X-GitHub-Delivery — must match")
            .isEqualTo(unified.dedupId())
            .isEqualTo("github-" + deliveryId);
        assertThat(legacy.body()).containsExactly(unified.body());
        assertThat(legacy.headers()).isEqualTo(unified.headers());
    }

    @Test
    @DisplayName("GitHub webhook: ping is short-circuited at the controller (no publish)")
    void githubPingShortCircuits() {
        WebhookProperties props = props(SHARED_SECRET);
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(new GithubWebhookSignatureVerifier(new GithubWebhookSecretSource(props))),
            List.of(new GithubSubjectKeyDeriver()),
            publisher,
            objectMapper
        );
        GitHubWebhookController controller = new GitHubWebhookController(pipeline);

        var resp = controller.receive("{}".getBytes(StandardCharsets.UTF_8), "ping", "delivery-id", "sha256=ignored");

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(publisher, times(0)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("GitLab webhook: legacy /gitlab controller + unified pipeline emit identical PublishRequest")
    void gitlabParity() {
        byte[] body = ("{\"object_kind\":\"push\","
            + "\"project\":{\"path_with_namespace\":\"ase/ipraktikum/ios2526/introcourse\"}}")
            .getBytes(StandardCharsets.UTF_8);
        String eventUuid = "aaaa1111-bbbb-2222-cccc-333344445555";

        WebhookProperties props = props(SHARED_SECRET);
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(new GitlabWebhookSignatureVerifier(List.of(new GitlabWebhookSecretSource(SHARED_SECRET)))),
            List.of(new GitlabSubjectKeyDeriver()),
            publisher,
            objectMapper
        );
        GitLabWebhookController controller = new GitLabWebhookController(pipeline);

        // Path A: legacy controller shim.
        controller.receive(body, SHARED_SECRET, "Push Hook", eventUuid, /* idempotency */ null, /* webhookUuid */ null);
        // Path B: unified pipeline directly.
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Gitlab-Token", SHARED_SECRET);
        headers.put("X-Gitlab-Event", "Push Hook");
        headers.put("X-Gitlab-Event-UUID", eventUuid);
        pipeline.handle(IntegrationKind.GITLAB, body, headers);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publisher, times(2)).publish(captor.capture());
        PublishRequest legacy = captor.getAllValues().get(0);
        PublishRequest unified = captor.getAllValues().get(1);

        assertThat(legacy.subject())
            .as("subject is namespace~joined + project + event")
            .isEqualTo(unified.subject())
            .isEqualTo("gitlab.ase~ipraktikum~ios2526.introcourse.push");
        assertThat(legacy.dedupId())
            .as("dedup id derives from X-Gitlab-Event-UUID — must match")
            .isEqualTo(unified.dedupId())
            .isEqualTo("gitlab-" + eventUuid);
        assertThat(legacy.body()).containsExactly(unified.body());
        assertThat(legacy.headers()).isEqualTo(unified.headers());
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
