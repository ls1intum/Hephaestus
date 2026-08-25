package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Handles Slack DM mentor turns consumed from the unified Slack NATS stream. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackMentorDmMessageHandler extends AbstractSlackEnvelopeHandler {

    private final SlackMentorService mentorService;

    public SlackMentorDmMessageHandler(SlackMentorService mentorService, NatsMessageDeserializer deserializer) {
        super("message_im", deserializer);
        this.mentorService = mentorService;
    }

    @Override
    protected void handleEnvelope(JsonNode root) {
        JsonNode event = root.path("event");
        if (
            !"message".equals(event.path("type").asString("")) || !"im".equals(event.path("channel_type").asString(""))
        ) {
            return;
        }
        if (event.has("bot_id") || !event.path("subtype").asString("").isEmpty()) {
            return;
        }
        String teamId = teamId(root);
        String channelId = event.path("channel").asString("");
        String slackUserId = event.path("user").asString("");
        String text = event.path("text").asString("");
        String messageTs = event.path("ts").asString("");
        String threadTs = event.path("thread_ts").asString(messageTs);
        mentorService.handleDm(teamId, channelId, slackUserId, text, messageTs, threadTs);
    }
}
