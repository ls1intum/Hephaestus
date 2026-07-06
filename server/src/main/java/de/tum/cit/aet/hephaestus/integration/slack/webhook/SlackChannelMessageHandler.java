package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Consumer-side handler for durable Slack monitored-channel {@code message} events
 * ({@code slack.<team>.<channel>.message}). Registered on the unified
 * {@code IntegrationMessageHandlerRegistry} via {@code EventTypeKey(SLACK, "message")};
 * the {@code IntegrationNatsConsumer} slack consumer feeds it off the ACK thread, so this runs
 * with the framework's at-least-once + poison/DLQ + backpressure + graceful-drain guarantees.
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
 *   <li>a content-bearing subtype ({@code thread_broadcast}, {@code me_message}) → ingested like a
 *       plain message; a bot-authored message or a non-content subtype (joins, topic changes, …) → no-op.
 * </ul>
 *
 * <p><strong>Idempotency.</strong> {@code insertIfAbsent} keys on {@code (workspace, channel, ts)}
 * via {@code ON CONFLICT DO NOTHING}, so a duplicate redelivery of the same event re-runs the exact
 * resolve/gate/store and applies once — no double-apply. Edits/deletes are scoped UPDATEs that no-op
 * on a not-yet-ingested row; JetStream's in-order per-subject delivery normally orders the base
 * insert first, so the one case this per-message path does not self-heal is a pathological reorder
 * (a NAK'd base insert redelivered after its own delete). The base class wraps {@link #handleEvent}
 * in a {@link TransactionTemplate}; any exception rolls back and propagates so the consumer NAKs and
 * JetStream redelivers.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelMessageHandler extends AbstractIntegrationMessageHandler<JsonNode> {

    /**
     * Subtypes that still carry a real author + {@code ts} + {@code text} and must ingest like a plain
     * message. Everything else with a non-empty subtype (channel_join, channel_topic, …) is metadata.
     */
    private static final Set<String> CONTENT_BEARING_SUBTYPES = Set.of("thread_broadcast", "me_message");

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
        // Never react to our own bot's messages.
        if (event.has("bot_id")) {
            return;
        }
        // A plain message (empty subtype) or a content-bearing subtype ingests; other subtypes are metadata.
        if (!subtype.isEmpty() && !CONTENT_BEARING_SUBTYPES.contains(subtype)) {
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
