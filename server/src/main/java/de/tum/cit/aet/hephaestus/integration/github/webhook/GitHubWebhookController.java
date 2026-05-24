package de.tum.cit.aet.hephaestus.integration.github.webhook;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.gitprovider.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.webhook.WebhookIngestPipeline;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound GitHub webhook endpoint — kept as a permanent URL anchor at {@code /github}
 * because vendor-side webhook configurations are pinned per-installation and
 * bulk-rewriting them across thousands of installs is infeasible. (Plan v4 D13.)
 *
 * <p>The actual ingest pipeline lives in {@link WebhookIngestPipeline}; this controller
 * is a 10-line shim that pre-reads the body + headers, short-circuits the
 * {@code ping} setup event (GitHub-specific liveness check), and forwards to the
 * unified pipeline. The pipeline does HMAC verification, subject + dedup-id
 * derivation, and JetStream publication — identical for both the legacy URL anchor
 * and the unified {@code /webhooks/github} entry point.
 *
 * <p>{@code @ConditionalOnBean(JetStreamPublisher.class)} mirrors the original
 * gating: this controller is active only when the webhook runtime role brings up
 * the publisher.
 */
@RestController
@ConditionalOnBean(JetStreamPublisher.class)
@WorkspaceAgnostic(
    "Webhook reception is provider-keyed (org/repo). Workspace context is resolved downstream by the sync consumer."
)
public class GitHubWebhookController {

    private static final String HEADER_EVENT = "X-GitHub-Event";
    private static final String HEADER_DELIVERY = "X-GitHub-Delivery";
    private static final String HEADER_SIGNATURE_256 = "X-Hub-Signature-256";
    private static final String PING_EVENT = "ping";

    private final WebhookIngestPipeline pipeline;

    public GitHubWebhookController(WebhookIngestPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping(path = "/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()") // HMAC-authenticated by the pipeline; transport auth is by signature.
    public ResponseEntity<?> receive(
        @RequestBody byte[] body,
        @RequestHeader(name = HEADER_EVENT, required = false) String eventType,
        @RequestHeader(name = HEADER_DELIVERY, required = false) String deliveryId,
        @RequestHeader(name = HEADER_SIGNATURE_256, required = false) String signature
    ) {
        // Setup-only liveness ping — GitHub posts this immediately after the App is
        // installed to confirm reachability. No payload to publish; 200 OK is enough.
        if (PING_EVENT.equalsIgnoreCase(eventType)) {
            return ResponseEntity.ok(Map.of("status", "pong"));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (eventType != null) headers.put(HEADER_EVENT, eventType);
        if (deliveryId != null) headers.put(HEADER_DELIVERY, deliveryId);
        if (signature != null) headers.put(HEADER_SIGNATURE_256, signature);
        return pipeline.handle(IntegrationKind.GITHUB, body, headers);
    }
}
