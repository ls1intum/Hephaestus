package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.core.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.core.webhook.PublishRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Fast-classifier + producer seam for the Slack Events endpoint. A monitored-channel
 * {@code message} event ({@code event.type == "message"} with {@code channel_type} in
 * {@code channel}/{@code group}, edits and deletes included) is PASSIVE, recoverable content:
 * it is republished onto the core durable transport
 * ({@link JetStreamPublisher} → subject {@code slack.<team>.<channel>.message}, {@code Nats-Msg-Id}
 * = {@code slack-<event_id>}) and processed off the ACK thread by {@code SlackChannelMessageHandler}
 * with the framework's at-least-once + poison/DLQ guarantees. Everything else (DM mentor turns,
 * App Home, {@code assistant_thread_started}, uninstall) is INTERACTIVE and stays in-process on
 * the ACK thread exactly as before.
 *
 * <p>Slack sends every event to the SINGLE events Request URL, so the controller must
 * fast-classify here rather than at the edge. The classification keys only on
 * {@code channel_type} so a DM ({@code im}) is never published — its mentor turn must run
 * in-process where the mentor stack lives.
 *
 * <p><strong>Topology.</strong> The events endpoint runs on the {@code application-server}
 * (server role), co-located with the mentor stack AND the {@code IntegrationNatsConsumer} slack
 * consumer, NOT on the isolated {@code webhook-server}. Unlike GitHub/GitLab push (not manually
 * redeliverable → the dedicated webhook-server exists so an app-server restart cannot drop them,
 * ADR 0008), Slack REDELIVERS un-acked events, so hosting the producer on the app-server is safe:
 * a pod-kill before the JetStream ack simply provokes a Slack redelivery. The {@link JetStreamPublisher}
 * bean is therefore made available on the server role wherever Slack ingest is enabled (via the
 * webhook role in the monolith, or {@code SlackNatsPublisherConfiguration} when the webhook role
 * is off). If it is nonetheless absent (NATS disabled), the controller returns 503 on the channel
 * branch so Slack redelivers — never a silent 200 drop.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SlackChannelEventPublisher.class);

    /** Outcome of the classify-and-publish attempt; the controller maps it to an HTTP status. */
    public enum PublishOutcome {
        /** Published to JetStream (or dedup-collapsed server-side); ACK 200. */
        PUBLISHED,
        /** Not a monitored-channel message; the caller runs the in-process dispatch. */
        NOT_CHANNEL_MESSAGE,
        /** A channel message, but no publisher bean is wired (NATS off) — reply 503, Slack redelivers. */
        PUBLISHER_UNAVAILABLE,
        /** A channel message whose publish failed after retries — reply 503, Slack redelivers. */
        PUBLISH_FAILED,
    }

    @Nullable
    private final JetStreamPublisher publisher;

    private final SlackSubjectKeyDeriver subjectKeyDeriver;

    public SlackChannelEventPublisher(
        @Nullable JetStreamPublisher publisher,
        SlackSubjectKeyDeriver subjectKeyDeriver
    ) {
        this.publisher = publisher;
        this.subjectKeyDeriver = subjectKeyDeriver;
    }

    /**
     * Publishes the raw {@code event_callback} body to JetStream iff {@code root} is a
     * monitored-channel message. Returns {@link PublishOutcome#NOT_CHANNEL_MESSAGE} otherwise so
     * the caller keeps the existing in-process routing for interactive events.
     */
    public PublishOutcome publishIfChannelMessage(JsonNode root, byte[] rawBody) {
        if (!isMonitoredChannelMessage(root)) {
            return PublishOutcome.NOT_CHANNEL_MESSAGE;
        }
        if (publisher == null) {
            log.warn(
                "Slack channel message received but no JetStreamPublisher is wired (NATS disabled?) — replying 503 so " +
                    "Slack redelivers; enable hephaestus.sync.nats.enabled to durably ingest channel content"
            );
            return PublishOutcome.PUBLISHER_UNAVAILABLE;
        }
        String subject = subjectKeyDeriver.subjectFor(root);
        String dedupId = subjectKeyDeriver.dedupIdFor(root);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Nats-Msg-Id", dedupId);
        try {
            publisher.publish(new PublishRequest(subject, dedupId, headers, rawBody));
        } catch (JetStreamPublisher.PublishFailedException e) {
            log.error("Slack channel-message publish failed: subject={} — {}", sanitizeForLog(subject), e.getMessage());
            return PublishOutcome.PUBLISH_FAILED;
        }
        log.debug(
            "Published Slack channel message to NATS: subject={} dedupId={}",
            sanitizeForLog(subject),
            sanitizeForLog(dedupId)
        );
        return PublishOutcome.PUBLISHED;
    }

    /**
     * Whether {@code root} is a monitored-channel/group {@code message} event (plain, edited, or
     * deleted). DMs ({@code channel_type == "im"}) are excluded so they stay on the in-process
     * mentor path.
     */
    private static boolean isMonitoredChannelMessage(JsonNode root) {
        JsonNode event = root.path("event");
        if (!"message".equals(event.path("type").asString(""))) {
            return false;
        }
        String channelType = event.path("channel_type").asString("");
        return "channel".equals(channelType) || "group".equals(channelType);
    }
}
