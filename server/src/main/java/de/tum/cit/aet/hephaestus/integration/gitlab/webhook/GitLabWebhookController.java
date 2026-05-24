package de.tum.cit.aet.hephaestus.integration.gitlab.webhook;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.DedupIdResolver;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.PublishRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Inbound GitLab webhook endpoint. Verifies {@code X-Gitlab-Token} against the shared secret in
 * constant time, then publishes to JetStream. Dedup ID priority: {@code Idempotency-Key} (GitLab
 * 17.4+) → {@code X-Gitlab-Event-UUID} (16.2+) → SHA-256 of body + event type. See ADR 0008.
 */
@RestController
@ConditionalOnBean(JetStreamPublisher.class)
@WorkspaceAgnostic(
    "Webhook reception is provider-keyed (group/project). Workspace context is resolved downstream by the sync consumer."
)
public class GitLabWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookController.class);

    private static final String HEADER_TOKEN = "X-Gitlab-Token";
    private static final String HEADER_EVENT = "X-Gitlab-Event";
    private static final String HEADER_EVENT_UUID = "X-Gitlab-Event-UUID";
    private static final String HEADER_IDEMPOTENCY = "Idempotency-Key";
    private static final String HEADER_WEBHOOK_UUID = "X-Gitlab-Webhook-UUID";
    private static final String NATS_MSG_ID = "Nats-Msg-Id";

    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final JetStreamPublisher publisher;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> rejectionCounters = new ConcurrentHashMap<>();

    public GitLabWebhookController(
        WebhookProperties webhookProperties,
        ObjectMapper objectMapper,
        JetStreamPublisher publisher,
        MeterRegistry meterRegistry
    ) {
        this.webhookProperties = webhookProperties;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping(path = "/gitlab", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()") // Token-authenticated at controller layer; transport auth is by shared secret
    public ResponseEntity<Map<String, String>> receive(
        @RequestBody byte[] body,
        @RequestHeader(name = HEADER_TOKEN, required = false) String token,
        @RequestHeader(name = HEADER_EVENT, required = false) String eventType,
        @RequestHeader(name = HEADER_EVENT_UUID, required = false) String eventUuid,
        @RequestHeader(name = HEADER_IDEMPOTENCY, required = false) String idempotencyKey,
        @RequestHeader(name = HEADER_WEBHOOK_UUID, required = false) String webhookUuid
    ) {
        String secret = webhookProperties.secret();
        if (secret == null || secret.isBlank()) {
            log.warn("GitLab webhook rejected: server has no shared secret configured");
            rejected("missing-secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("missing-secret"));
        }
        if (!GitLabTokenVerifier.verify(token, secret)) {
            log.warn("GitLab webhook rejected: invalid token");
            rejected("invalid-token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("invalid-token"));
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception e) {
            rejected("invalid-json");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid-json"));
        }

        String subject = GitLabSubjectBuilder.build(payload);
        String payloadEventType = payload.path("object_kind").asText("");
        if (payloadEventType.isEmpty()) {
            payloadEventType = payload.path("event_name").asText("");
        }
        String effectiveEvent = (eventType != null && !eventType.isBlank()) ? eventType : payloadEventType;

        String dedupSourceId = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : eventUuid;
        String dedupId = (dedupSourceId != null && !dedupSourceId.isBlank())
            ? "gitlab-" + dedupSourceId
            : DedupIdResolver.build("gitlab", body, effectiveEvent);

        Map<String, String> headers = new LinkedHashMap<>();
        if (!effectiveEvent.isEmpty()) {
            headers.put(HEADER_EVENT, effectiveEvent);
        }
        if (webhookUuid != null && !webhookUuid.isBlank()) {
            headers.put(HEADER_WEBHOOK_UUID, webhookUuid);
        }
        headers.put(NATS_MSG_ID, dedupId);

        try {
            publisher.publish(new PublishRequest(subject, dedupId, headers, body));
        } catch (JetStreamPublisher.PublishFailedException e) {
            log.error("GitLab webhook publish failed (subject={}): {}", subject, e.getMessage());
            rejected("publish-failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error("publish-failed"));
        }

        log.debug("Published GitLab webhook: subject={} event={}", subject, effectiveEvent);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void rejected(String reason) {
        rejectionCounters
            .computeIfAbsent(reason, r ->
                Counter.builder("webhook.rejected").tag("provider", "gitlab").tag("reason", r).register(meterRegistry)
            )
            .increment();
    }

    private static Map<String, String> error(String code) {
        return Map.of("error", code);
    }
}
