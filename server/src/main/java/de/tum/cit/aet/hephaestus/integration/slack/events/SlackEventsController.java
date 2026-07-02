package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackOnboardingService;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RestController
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
@WorkspaceAgnostic("Slack events; the workspace is resolved from the payload's team_id, not the URL")
public class SlackEventsController {

    private static final Logger log = LoggerFactory.getLogger(SlackEventsController.class);
    private static final int DEDUP_CAP = 4096;

    private final SlackSignatureVerifier verifier;
    private final SlackMentorService mentorService;
    private final SlackIngestService ingestService;
    private final SlackOnboardingService onboardingService;
    private final ObjectMapper objectMapper;

    // Slack retries un-acked events; drop duplicates by event_id (bounded).
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();

    public SlackEventsController(
        SlackSignatureVerifier verifier,
        SlackMentorService mentorService,
        SlackIngestService ingestService,
        SlackOnboardingService onboardingService,
        ObjectMapper objectMapper
    ) {
        this.verifier = verifier;
        this.mentorService = mentorService;
        this.ingestService = ingestService;
        this.onboardingService = onboardingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/slack/events")
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
            if (!eventId.isEmpty() && !markSeen(eventId)) {
                return ResponseEntity.ok().build(); // duplicate retry
            }
            try {
                dispatchEvent(root);
            } catch (Exception e) {
                log.warn("Slack event handling failed: {}", e.getMessage(), e);
            }
        }
        // Always ACK 200 within Slack's 3s window; work runs synchronously-but-fast (the mentor turn is async).
        return ResponseEntity.ok().build();
    }

    private void dispatchEvent(JsonNode root) {
        String teamId = root.path("team_id").asString("");
        JsonNode event = root.path("event");
        String eventType = event.path("type").asString("");
        if ("app_home_opened".equals(eventType)) {
            // Only (re)publish on the Home tab open; the Messages tab open fires the same event with tab=messages.
            if ("home".equals(event.path("tab").asString("home"))) {
                onboardingService.onHomeOpened(teamId, event.path("user").asString(""));
            }
            return;
        }
        if (!"message".equals(eventType)) {
            return;
        }
        // Never react to our own bot's messages, edits, deletes, or joins.
        if (event.has("bot_id") || !event.path("subtype").asString("").isEmpty()) {
            return;
        }
        String channelType = event.path("channel_type").asString("");
        String channelId = event.path("channel").asString("");
        String slackUserId = event.path("user").asString("");
        String text = event.path("text").asString("");

        if ("im".equals(channelType)) {
            mentorService.handleDm(teamId, channelId, slackUserId, text, event.path("ts").asString(""));
        } else if ("channel".equals(channelType) || "group".equals(channelType)) {
            ingestService.ingestChannelMessage(
                teamId,
                channelId,
                event.path("ts").asString(""),
                event.path("thread_ts").asString(null),
                slackUserId,
                text
            );
        }
    }

    private boolean markSeen(String eventId) {
        if (seenEventIds.size() > DEDUP_CAP) {
            seenEventIds.clear();
        }
        return seenEventIds.add(eventId);
    }
}
