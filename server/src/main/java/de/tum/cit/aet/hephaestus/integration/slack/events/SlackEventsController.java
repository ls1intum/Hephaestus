package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
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
 * Inbound Slack Events API entry ({@code POST /slack/events}), reached over the public tunnel. Verifies the
 * request signature, answers the one-time {@code url_verification} handshake, and routes {@code event_callback}s:
 * a DM to the bot drives a mentor turn; a monitored channel message is ingested as content. Auth is the signature
 * (this path is on the unauthenticated worker-hub security chain, like {@code /webhooks/**}).
 */
@Hidden // inbound Slack webhook receiver — not part of the webapp API surface; excluded from the OpenAPI client
@RestController
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Slack events; the workspace is resolved from the payload's team_id, not the URL")
public class SlackEventsController {

    private static final Logger log = LoggerFactory.getLogger(SlackEventsController.class);

    private final SlackSignatureVerifier verifier;
    private final SlackEventDispatcher dispatcher;
    private final SlackEventDedupService dedupService;
    private final ObjectMapper objectMapper;

    public SlackEventsController(
        SlackSignatureVerifier verifier,
        SlackEventDispatcher dispatcher,
        SlackEventDedupService dedupService,
        ObjectMapper objectMapper
    ) {
        this.verifier = verifier;
        this.dispatcher = dispatcher;
        this.dedupService = dedupService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/slack/events")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> events(
        @RequestBody(required = false) byte[] rawBody,
        @RequestHeader HttpHeaders headers
    ) {
        if (rawBody == null) {
            return ResponseEntity.badRequest().build();
        }
        String timestamp = headers.getFirst("X-Slack-Request-Timestamp");
        String signature = headers.getFirst("X-Slack-Signature");
        if (!verifier.verify(timestamp, signature, rawBody, Instant.now().getEpochSecond())) {
            return ResponseEntity.status(401).build();
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        String type = root.path("type").asString("");
        if ("url_verification".equals(type)) {
            // Echo the challenge to complete the Request-URL handshake.
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(root.path("challenge").asString(""));
        }

        if ("event_callback".equals(type)) {
            String eventId = root.path("event_id").asString("");
            // Durable, multi-replica dedup: exactly one replica claims a given event_id (Slack retries
            // un-acked events, and two pods can each receive the same delivery).
            if (!eventId.isEmpty() && !dedupService.claim(eventId)) {
                return ResponseEntity.ok().build(); // duplicate retry / already claimed by another replica
            }
            try {
                dispatcher.dispatch(root);
            } catch (Exception e) {
                // Best-effort: we log and still 200, so a synchronous channel-ingest failure is NOT retried by
                // Slack (it only redelivers on a non-2xx/timeout) — channel ingest is best-effort, not at-least-once.
                log.warn("Slack event handling failed: {}", e.getMessage(), e);
            }
        }
        // Always ACK 200 within Slack's 3s window; the slow/remote branches (mentor DM, App Home, prompt seed) are
        // offloaded off this thread, so only the fast synchronous work (dedup claim, channel ingest) runs before it.
        return ResponseEntity.ok().build();
    }
}
