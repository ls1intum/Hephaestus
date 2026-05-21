package de.tum.cit.aet.hephaestus.gitprovider.webhook.github;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.DedupIdResolver;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.HmacVerifier;
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
 * Inbound GitHub webhook endpoint. Verifies {@code X-Hub-Signature-256} HMAC against the raw
 * body bytes, short-circuits {@code ping} setup events, and publishes everything else to
 * JetStream. Workspace context is resolved downstream from the NATS subject by the sync consumer,
 * not at this layer. See ADR 0008.
 */
@RestController
@ConditionalOnBean(JetStreamPublisher.class)
@WorkspaceAgnostic(
    "Webhook reception is provider-keyed (org/repo). Workspace context is resolved downstream by the sync consumer."
)
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private static final String HEADER_SIGNATURE_256 = "X-Hub-Signature-256";
    private static final String HEADER_EVENT = "X-GitHub-Event";
    private static final String HEADER_DELIVERY = "X-GitHub-Delivery";
    private static final String NATS_MSG_ID = "Nats-Msg-Id";
    private static final String PING_EVENT = "ping";

    private final WebhookProperties webhookProperties;
    private final ObjectMapper objectMapper;
    private final JetStreamPublisher publisher;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> rejectionCounters = new ConcurrentHashMap<>();

    public GitHubWebhookController(
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

    @PostMapping(path = "/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()") // HMAC-authenticated at controller layer; transport auth is by signature
    public ResponseEntity<Map<String, String>> receive(
        @RequestBody byte[] body,
        @RequestHeader(name = HEADER_SIGNATURE_256, required = false) String signature,
        @RequestHeader(name = HEADER_EVENT, required = false) String eventType,
        @RequestHeader(name = HEADER_DELIVERY, required = false) String deliveryId
    ) {
        String secret = webhookProperties.secret();
        if (secret == null || secret.isBlank()) {
            log.warn("GitHub webhook rejected: server has no shared secret configured");
            rejected("missing-secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("missing-secret"));
        }
        if (!HmacVerifier.verify(signature, secret, body)) {
            log.warn("GitHub webhook rejected: invalid signature (deliveryId={})", deliveryId);
            rejected("invalid-signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("invalid-signature"));
        }
        if (eventType == null || eventType.isBlank()) {
            rejected("missing-event-header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("missing-event-header"));
        }
        if (PING_EVENT.equals(eventType)) {
            return ResponseEntity.ok(Map.of("status", "pong"));
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception e) {
            rejected("invalid-json");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid-json"));
        }

        String subject = GitHubSubjectBuilder.build(payload, eventType);
        String dedupId = (deliveryId != null && !deliveryId.isBlank())
            ? "github-" + deliveryId
            : DedupIdResolver.build("github", body, eventType);

        Map<String, String> headers = new LinkedHashMap<>();
        if (deliveryId != null && !deliveryId.isBlank()) {
            headers.put(HEADER_DELIVERY, deliveryId);
        }
        headers.put(HEADER_EVENT, eventType);
        headers.put(NATS_MSG_ID, dedupId);

        try {
            publisher.publish(new PublishRequest(subject, dedupId, headers, body));
        } catch (JetStreamPublisher.PublishFailedException e) {
            log.error(
                "GitHub webhook publish failed (deliveryId={} subject={}): {}",
                deliveryId,
                subject,
                e.getMessage()
            );
            rejected("publish-failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error("publish-failed"));
        }

        log.info("Published GitHub webhook: subject={} deliveryId={} event={}", subject, deliveryId, eventType);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void rejected(String reason) {
        rejectionCounters
            .computeIfAbsent(reason, r ->
                Counter.builder("webhook.rejected").tag("provider", "github").tag("reason", r).register(meterRegistry)
            )
            .increment();
    }

    private static Map<String, String> error(String code) {
        return Map.of("error", code);
    }
}
