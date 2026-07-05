package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackChannelEventPublisher;
import de.tum.cit.aet.hephaestus.integration.slack.webhook.SlackChannelEventPublisher.PublishOutcome;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
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
 * Inbound Slack Events API entry ({@code POST /slack/events}), reached over the public tunnel. Verifies the
 * request signature, answers the one-time {@code url_verification} handshake, and FAST-CLASSIFIES every
 * {@code event_callback} by recoverability (Slack sends every event to this single Request URL):
 *
 * <ul>
 *   <li><strong>Passive monitored-channel {@code message}</strong> (plain/edit/delete on a channel or group) →
 *       republished to the core durable transport ({@link SlackChannelEventPublisher} →
 *       {@code JetStreamPublisher}, {@code Nats-Msg-Id = slack-<event_id>}) and processed OFF this thread by
 *       {@code SlackChannelMessageHandler}, inheriting the framework's at-least-once + poison/DLQ + graceful
 *       drain. This closes the durability bug the old path had: it committed a dedup marker BEFORE the
 *       synchronous ingest and then log-and-200'd on failure, so a pod-kill (or transient error) between claim
 *       and effect permanently lost the event (Slack's retry hit an already-claimed marker → 200 → skipped).</li>
 *   <li><strong>Interactive</strong> (DM mentor turn, {@code assistant_thread_started}, {@code app_home_opened},
 *       uninstall/token-revocation) → dispatched IN-PROCESS via {@link SlackEventDispatcher} exactly as before;
 *       the mentor path is deliberately NOT routed through NATS.</li>
 * </ul>
 *
 * <p>Server-side {@code Nats-Msg-Id} dedup (JetStream duplicate window) plus the handler's idempotent
 * {@code insertIfAbsent} give exactly-once ingest with no bespoke dedup table. When the publisher bean is
 * absent (NATS disabled) a channel message returns 503 so Slack redelivers — never a silent 200 drop. Auth is
 * the signature (this path is on the unauthenticated worker-hub security chain, like {@code /webhooks/**}).
 */
@Hidden // inbound Slack webhook receiver — not part of the webapp API surface; excluded from the OpenAPI client
@RestController
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Slack events; the workspace is resolved from the payload's team_id, not the URL")
public class SlackEventsController {

    private static final Logger log = LoggerFactory.getLogger(SlackEventsController.class);

    private final SlackSignatureVerifier verifier;
    private final SlackEventDispatcher dispatcher;
    private final SlackChannelEventPublisher channelEventPublisher;
    private final ObjectMapper objectMapper;

    public SlackEventsController(
        SlackSignatureVerifier verifier,
        SlackEventDispatcher dispatcher,
        SlackChannelEventPublisher channelEventPublisher,
        ObjectMapper objectMapper
    ) {
        this.verifier = verifier;
        this.dispatcher = dispatcher;
        this.channelEventPublisher = channelEventPublisher;
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
            PublishOutcome outcome = channelEventPublisher.publishIfChannelMessage(root, rawBody);
            switch (outcome) {
                case PUBLISHED:
                    // Durably enqueued; the ACK-thread is free. Server-side Nats-Msg-Id dedup + the handler's
                    // idempotent insertIfAbsent make a Slack redelivery a no-op — at-least-once without loss.
                    return ResponseEntity.ok().build();
                case PUBLISHER_UNAVAILABLE:
                case PUBLISH_FAILED:
                    // Do NOT 200-and-drop: reply 503 so Slack redelivers the channel content when the durable
                    // pipe recovers. (Interactive events never reach here — only channel messages publish.)
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
                case NOT_CHANNEL_MESSAGE:
                default:
                    // Interactive event — dispatch in-process. Slow/remote branches (mentor DM, App Home, prompt
                    // seed) are offloaded off this thread inside the dispatcher, so only fast work precedes the 200.
                    try {
                        dispatcher.dispatch(root);
                    } catch (Exception e) {
                        // Interactive events are best-effort and Slack does not redeliver after a 200; log and ACK.
                        log.warn("Slack interactive event handling failed: {}", e.getMessage(), e);
                    }
                    return ResponseEntity.ok().build();
            }
        }
        // Always ACK 200 within Slack's 3s window for anything else (e.g. unknown envelope types).
        return ResponseEntity.ok().build();
    }
}
