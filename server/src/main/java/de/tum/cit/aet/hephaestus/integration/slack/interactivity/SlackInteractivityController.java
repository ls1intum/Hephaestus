package de.tum.cit.aet.hephaestus.integration.slack.interactivity;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackSignatureVerifier;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Inbound Slack interactivity entry ({@code POST /slack/interactivity}), reached over the public tunnel on a
 * SEPARATE Slack Request URL from {@code /slack/events}. Slack posts interactive-component payloads
 * (block_actions from the feedback buttons / uptake block, view_submission from the dispute modal) as a
 * {@code application/x-www-form-urlencoded} body with a single {@code payload=<url-encoded-json>} field.
 *
 * <p>Verifies the request signature over the RAW body bytes (the same HMAC scheme as the events endpoint), then
 * ACKs 200 within Slack's 3&nbsp;s window and does the actual work asynchronously — a modal open or a DB write must
 * never hold the ACK. Auth is the signature (this path sits on the unauthenticated worker-hub security chain,
 * alongside {@code /slack/events}).
 */
@RestController
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Slack interactivity; the workspace is resolved from the payload's team_id, not the URL")
public class SlackInteractivityController {

    private static final Logger log = LoggerFactory.getLogger(SlackInteractivityController.class);

    private final SlackSignatureVerifier verifier;
    private final SlackFeedbackHandler handler;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public SlackInteractivityController(
        SlackSignatureVerifier verifier,
        SlackFeedbackHandler handler,
        ObjectMapper objectMapper,
        Executor slackInteractivityExecutor
    ) {
        this.verifier = verifier;
        this.handler = handler;
        this.objectMapper = objectMapper;
        this.executor = slackInteractivityExecutor;
    }

    @PostMapping(value = "/slack/interactivity")
    public ResponseEntity<String> interactivity(
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

        String payloadJson = extractPayload(new String(rawBody, StandardCharsets.UTF_8));
        if (payloadJson.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        final JsonNode payload;
        try {
            payload = objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        String type = payload.path("type").asString("");
        // ACK immediately; run the handler off the request thread so a Slack round-trip never breaches the 3s ACK.
        executor.execute(() -> dispatch(type, payload));
        // An empty 200 both ACKs an action and closes a submitted modal.
        return ResponseEntity.ok().build();
    }

    private void dispatch(String type, JsonNode payload) {
        try {
            switch (type) {
                case "block_actions" -> handler.handleBlockActions(payload);
                case "view_submission" -> handler.handleViewSubmission(payload);
                default -> log.debug("slack.interactivity: ignoring payload type {}", type);
            }
        } catch (Exception e) {
            log.warn("Slack interactivity handling failed: {}", e.getMessage(), e);
        }
    }

    /** Pull the URL-decoded {@code payload} field out of a {@code application/x-www-form-urlencoded} body. */
    static String extractPayload(String body) {
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "payload".equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
