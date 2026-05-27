package de.tum.cit.aet.hephaestus.integration.core.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified webhook ingest controller.
 *
 * <p>{@code POST /webhooks/{kind}} is the sole webhook entry point. Per-kind
 * routing resolves the {@link IntegrationKindRouting kind} from the path and
 * delegates to {@link WebhookIngestPipeline}, which dispatches signature
 * verification, subject derivation and JetStream publication through the per-kind
 * {@link de.tum.cit.aet.hephaestus.integration.core.spi.WebhookSignatureVerifier} and
 * {@link de.tum.cit.aet.hephaestus.integration.core.spi.SubjectKeyDeriver} beans.
 * GitHub, GitLab, Slack and Outline all share this endpoint.
 *
 * <p>Active only on the webhook runtime role.
 */
@RestController
@ConditionalOnProperty(name = RuntimeRole.WEBHOOK_PROPERTY, havingValue = "true", matchIfMissing = true)
public class WebhookController {

    private final WebhookIngestPipeline pipeline;
    private final IntegrationKindRouting routing;

    public WebhookController(WebhookIngestPipeline pipeline, IntegrationKindRouting routing) {
        this.pipeline = pipeline;
        this.routing = routing;
    }

    @PostMapping("/webhooks/{kind}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> ingest(@PathVariable String kind, HttpServletRequest req) throws IOException {
        return routing
            .resolve(kind)
            .<ResponseEntity<?>>map(k -> doHandle(k, req))
            .orElseGet(() ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown integration kind: " + kind))
            );
    }

    private ResponseEntity<?> doHandle(
        de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind k,
        HttpServletRequest req
    ) {
        try {
            return pipeline.handle(k, req);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of("error", "could not read body: " + e.getMessage())
            );
        }
    }
}
