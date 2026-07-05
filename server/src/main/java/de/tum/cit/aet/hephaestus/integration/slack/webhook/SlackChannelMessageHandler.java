package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Consumer-side handler for durable Slack monitored-channel {@code message} events
 * ({@code slack.<team>.<channel>.message}). Registered on the unified
 * {@code IntegrationMessageHandlerRegistry} via {@code EventTypeKey(SLACK, "message")};
 * the {@code IntegrationNatsConsumer} slack consumer feeds it off the ACK thread, so this runs
 * with the framework's at-least-once + poison/DLQ + backpressure + graceful-drain guarantees —
 * the durability the previous synchronous, log-and-200 ingest lacked (a pod-kill between the
 * old dedup-claim and the ingest permanently lost the event).
 *
 * <p>The whole {@code event_callback} envelope is republished onto JetStream by
 * {@link SlackChannelEventPublisher}, so this handler re-derives the message subtype from the
 * payload and drives the persistence chain in {@link SlackIngestService}:
 * <ul>
 *   <li>plain channel/group message → {@code ingestChannelMessage} (resolve → BOTH consent
 *       gates fail-closed → forward-only watermark → participant firewall → idempotent
 *       {@code insertIfAbsent} + thread {@code upsertOnMessage} projection);
 *   <li>{@code message_changed} → {@code editMessage} (GDPR Art. 16 edit);
 *   <li>{@code message_deleted} → {@code tombstoneMessage} (GDPR Art. 17 tombstone), keyed on
 *       the deleted message's {@code ts} ({@code deleted_ts}, fallback
 *       {@code previous_message.ts}), never the delivery's own {@code ts};
 *   <li>a bot-authored message or any other subtype (joins, topic changes, …) → no-op.
 * </ul>
 *
 * <p><strong>Idempotency</strong> is the committed effect: {@code insertIfAbsent} keys on
 * {@code (workspace, channel, ts)} via {@code ON CONFLICT DO NOTHING}, so a JetStream
 * redelivery (or a duplicate Slack delivery that slipped the {@code Nats-Msg-Id} window)
 * re-runs the exact same resolve/gate/store and applies once — no double-apply, no loss. The
 * base class wraps {@link #handleEvent} in a {@link TransactionTemplate}; any exception rolls
 * the transaction back and propagates so the consumer NAKs and JetStream redelivers.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelMessageHandler extends AbstractIntegrationMessageHandler<JsonNode> {

    private final SlackIngestService ingestService;

    public SlackChannelMessageHandler(
        SlackIngestService ingestService,
        NatsMessageDeserializer deserializer,
        TransactionTemplate transactionTemplate
    ) {
        super(IntegrationKind.SLACK, "message", JsonNode.class, deserializer, transactionTemplate);
        this.ingestService = ingestService;
    }

    @Override
    protected void handleEvent(JsonNode root) {
        String teamId = root.path("team_id").asString("");
        JsonNode event = root.path("event");
        String subtype = event.path("subtype").asString("");
        String channelId = event.path("channel").asString("");

        // Edits/deletes arrive as message SUBTYPES; route them before the subtype no-op below (which drops every
        // other subtyped message). Slack nests the changed/deleted payload under event.message / previous_message,
        // so the bot_id guard does not apply — a message we never stored simply no-ops the scoped UPDATE.
        if ("message_deleted".equals(subtype)) {
            String deletedTs = event
                .path("deleted_ts")
                .asString(event.path("previous_message").path("ts").asString(""));
            ingestService.tombstoneMessage(teamId, channelId, deletedTs);
            return;
        }
        if ("message_changed".equals(subtype)) {
            JsonNode changed = event.path("message");
            ingestService.editMessage(
                teamId,
                channelId,
                changed.path("ts").asString(""),
                changed.path("text").asString("")
            );
            return;
        }
        // Never react to our own bot's messages, or to any other subtype (joins, channel_topic, thread_broadcast…).
        if (event.has("bot_id") || !subtype.isEmpty()) {
            return;
        }
        ingestService.ingestChannelMessage(
            teamId,
            channelId,
            event.path("ts").asString(""),
            event.path("thread_ts").asString(null),
            event.path("user").asString(""),
            event.path("text").asString("")
        );
    }
}
