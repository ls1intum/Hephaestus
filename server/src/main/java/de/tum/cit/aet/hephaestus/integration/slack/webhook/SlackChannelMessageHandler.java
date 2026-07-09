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

/** Durable consumer for monitored Slack channel/group message events. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelMessageHandler extends AbstractIntegrationMessageHandler<JsonNode> {

    /** Message subtypes that still carry user-authored text. */
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
        String teamId = AbstractSlackEnvelopeHandler.teamId(root);
        JsonNode event = root.path("event");
        String subtype = event.path("subtype").asString("");
        String channelId = event.path("channel").asString("");

        if ("message_deleted".equals(subtype)) {
            String deletedTs = event
                .path("deleted_ts")
                .asString(event.path("previous_message").path("ts").asString(""));
            ingestService.tombstoneMessage(teamId, channelId, deletedTs);
            return;
        }
        if ("message_changed".equals(subtype)) {
            JsonNode changed = event.path("message");
            if (changed.has("bot_id")) {
                return;
            }
            ingestService.editMessage(
                teamId,
                channelId,
                changed.path("ts").asString(""),
                changed.path("thread_ts").asString(null),
                changed.path("user").asString(""),
                changed.path("text").asString("")
            );
            return;
        }
        if (event.has("bot_id")) {
            return;
        }
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
