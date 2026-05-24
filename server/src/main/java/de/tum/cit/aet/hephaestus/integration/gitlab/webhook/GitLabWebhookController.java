package de.tum.cit.aet.hephaestus.integration.gitlab.webhook;

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
 * Inbound GitLab webhook endpoint — kept as a permanent URL anchor at {@code /gitlab}
 * because vendor-side webhook configurations are pinned per-project and
 * bulk-rewriting them across hundreds of projects is infeasible. (Plan v4 D13.)
 *
 * <p>The actual ingest pipeline lives in {@link WebhookIngestPipeline}; this
 * controller is a 5-line shim that pre-reads the body + GitLab-specific headers and
 * forwards to the unified pipeline. The pipeline does X-Gitlab-Token verification,
 * subject + dedup-id derivation (Idempotency-Key → X-Gitlab-Event-UUID → SHA-256),
 * and JetStream publication — identical for both the legacy URL anchor and the
 * unified {@code /webhooks/gitlab} entry point.
 *
 * <p>{@code @ConditionalOnBean(JetStreamPublisher.class)} mirrors the original
 * gating: this controller is active only when the webhook runtime role brings up
 * the publisher.
 */
@RestController
@ConditionalOnBean(JetStreamPublisher.class)
@WorkspaceAgnostic(
    "Webhook reception is provider-keyed (group/project). Workspace context is resolved downstream by the sync consumer."
)
public class GitLabWebhookController {

    private static final String HEADER_TOKEN = "X-Gitlab-Token";
    private static final String HEADER_EVENT = "X-Gitlab-Event";
    private static final String HEADER_EVENT_UUID = "X-Gitlab-Event-UUID";
    private static final String HEADER_IDEMPOTENCY = "Idempotency-Key";
    private static final String HEADER_WEBHOOK_UUID = "X-Gitlab-Webhook-UUID";

    private final WebhookIngestPipeline pipeline;

    public GitLabWebhookController(WebhookIngestPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping(path = "/gitlab", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()") // Token-authenticated by the pipeline; transport auth is by shared secret.
    public ResponseEntity<?> receive(
        @RequestBody byte[] body,
        @RequestHeader(name = HEADER_TOKEN, required = false) String token,
        @RequestHeader(name = HEADER_EVENT, required = false) String eventType,
        @RequestHeader(name = HEADER_EVENT_UUID, required = false) String eventUuid,
        @RequestHeader(name = HEADER_IDEMPOTENCY, required = false) String idempotencyKey,
        @RequestHeader(name = HEADER_WEBHOOK_UUID, required = false) String webhookUuid
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (token != null) headers.put(HEADER_TOKEN, token);
        if (eventType != null) headers.put(HEADER_EVENT, eventType);
        if (eventUuid != null) headers.put(HEADER_EVENT_UUID, eventUuid);
        if (idempotencyKey != null) headers.put(HEADER_IDEMPOTENCY, idempotencyKey);
        if (webhookUuid != null) headers.put(HEADER_WEBHOOK_UUID, webhookUuid);
        return pipeline.handle(IntegrationKind.GITLAB, body, headers);
    }
}
