package de.tum.cit.aet.hephaestus.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import tools.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.webhook.PublishRequest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.VerificationResult;
import de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier.WebhookRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link WebhookIngestPipeline}. Drives the verify → derive → publish
 * path with mock verifiers + derivers so we can assert what hits JetStream.
 *
 * <p>The tests are deliberately independent of the production GitHub/GitLab verifier
 * implementations — they validate the PIPELINE wiring, not the per-kind crypto.
 * The crypto adapters are covered in their own test classes.
 */
@DisplayName("WebhookIngestPipeline — unit")
class WebhookIngestPipelineTest extends BaseUnitTest {

    @Mock
    private JetStreamPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("verified webhook is published to JetStream with kind-derived subject + dedup-id")
    void publishesOnVerified() {
        SubjectKeyDeriver deriver = stubDeriver(IntegrationKind.GITHUB,
            "github.acme.repo.push", "github-DEADBEEF");
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Verified())),
            List.of(deriver),
            publisher,
            objectMapper
        );

        Map<String, String> headers = headers("X-GitHub-Event", "push", "X-GitHub-Delivery", "DEADBEEF");
        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.GITHUB,
            "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8), headers);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(publisher).publish(captor.capture());
        PublishRequest pr = captor.getValue();
        assertThat(pr.subject()).isEqualTo("github.acme.repo.push");
        assertThat(pr.dedupId()).isEqualTo("github-DEADBEEF");
        assertThat(pr.headers()).containsEntry("Nats-Msg-Id", "github-DEADBEEF");
        assertThat(pr.headers()).containsEntry("X-GitHub-Event", "push");
        assertThat(pr.headers()).containsEntry("X-GitHub-Delivery", "DEADBEEF");
    }

    @Test
    @DisplayName("invalid signature → 401 with verifier-reported reason, no publish")
    void invalidSignatureBlocksPublish() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Invalid("signature-mismatch"))),
            List.of(stubDeriver(IntegrationKind.GITHUB, "s", "d")),
            publisher,
            objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.GITHUB,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("missing verifier → 501 without touching the publisher")
    void missingVerifier() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(), List.of(), publisher, objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.SLACK,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("missing deriver → 501 (kind verified but no subject-derivation wired)")
    void missingDeriver() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Verified())),
            List.of(),
            publisher,
            objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.GITHUB,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("missing publisher → 503 so vendor retries")
    void missingPublisher() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Verified())),
            List.of(stubDeriver(IntegrationKind.GITHUB, "s", "d")),
            /* publisher */ null,
            objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.GITHUB,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("publisher exception → 503 so vendor retries")
    void publisherExceptionMapsTo503() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.GITLAB, new VerificationResult.Verified())),
            List.of(stubDeriver(IntegrationKind.GITLAB, "gitlab.foo.bar.push", "gitlab-uuid")),
            publisher,
            objectMapper
        );
        doThrow(new JetStreamPublisher.PublishFailedException("nats down", new RuntimeException()))
            .when(publisher).publish(any());

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.GITLAB,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("invalid JSON body → 400, no publish")
    void invalidJsonBlocksPublish() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Verified())),
            List.of(stubDeriver(IntegrationKind.GITHUB, "s", "d")),
            publisher,
            objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.GITHUB,
            "not json".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("stale-timestamp verdict → 408")
    void staleTimestamp408() {
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.SLACK, new VerificationResult.StaleTimestamp(900L))),
            List.of(),
            publisher,
            objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.SLACK,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        // Wave 1B collapsed all auth failures (including stale timestamp) to opaque 401
        // so attackers cannot probe the distinction between "missing", "bad signature",
        // and "stale" — would otherwise be a side-channel into the signing scheme.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("respond-immediately verdict → status + body served directly, no publish")
    void respondImmediatelyShortCircuits() {
        byte[] echoBody = "challenge-ack".getBytes(StandardCharsets.UTF_8);
        WebhookIngestPipeline pipeline = new WebhookIngestPipeline(
            List.of(stubVerifier(IntegrationKind.SLACK,
                new VerificationResult.RespondImmediately(200, "text/plain", echoBody))),
            List.of(),
            publisher,
            objectMapper
        );

        ResponseEntity<?> resp = pipeline.handle(IntegrationKind.SLACK,
            "{}".getBytes(StandardCharsets.UTF_8), headers());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(echoBody);
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("duplicate verifier registration is rejected at construction")
    void duplicateVerifierRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new WebhookIngestPipeline(
                List.of(
                    stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Verified()),
                    stubVerifier(IntegrationKind.GITHUB, new VerificationResult.Verified())
                ),
                List.of(),
                publisher,
                objectMapper
            )
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate WebhookSignatureVerifier");
    }

    @Test
    @DisplayName("duplicate deriver registration is rejected at construction")
    void duplicateDeriverRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new WebhookIngestPipeline(
                List.of(),
                List.of(
                    stubDeriver(IntegrationKind.GITHUB, "s", "d"),
                    stubDeriver(IntegrationKind.GITHUB, "s", "d")
                ),
                publisher,
                objectMapper
            )
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate SubjectKeyDeriver");
    }

    // ── Stubs ───────────────────────────────────────────────────────────────

    private static WebhookSignatureVerifier stubVerifier(IntegrationKind kind, VerificationResult result) {
        return new WebhookSignatureVerifier() {
            @Override public IntegrationKind kind() { return kind; }
            @Override public VerificationResult verify(WebhookRequest request) { return result; }
        };
    }

    private static SubjectKeyDeriver stubDeriver(IntegrationKind kind, String subject, String dedupId) {
        return new SubjectKeyDeriver() {
            @Override public IntegrationKind kind() { return kind; }
            @Override public String deriveSubject(tools.jackson.databind.JsonNode payload, Map<String, String> headers) {
                return subject;
            }
            @Override public String deriveDedupKey(byte[] body, Map<String, String> headers) {
                return dedupId;
            }
        };
    }

    private static Map<String, String> headers(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
