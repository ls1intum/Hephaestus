package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.handler.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackTs;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorService;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Routes Slack's {@code agent_session_stopped}: the developer pressed Stop on Heph's agent session in a thread of their
 * DM. An event without its fields, or with a field of another type, is acknowledged and changes nothing.
 */
@Component
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackAgentSessionStoppedMessageHandler extends AbstractSlackEnvelopeHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackAgentSessionStoppedMessageHandler.class);

    private static final String EVENT_TYPE = "agent_session_stopped";

    private final SlackMentorService mentorService;

    public SlackAgentSessionStoppedMessageHandler(
            SlackMentorService mentorService, NatsMessageDeserializer deserializer) {
        super(EVENT_TYPE, deserializer);
        this.mentorService = mentorService;
    }

    @Override
    protected void handleEnvelope(JsonNode root) {
        JsonNode event = root.path("event");
        if (!EVENT_TYPE.equals(event.path("type").asString(""))) {
            return;
        }
        String teamId = teamId(root);
        String user = text(event, "user");
        String channel = text(event, "channel");
        String threadTs = text(event, "thread_ts");
        String eventTs = text(event, "event_ts");
        List<String> streams = streams(event.path("streaming_message_ts"));
        if (teamId.isBlank()
                || user == null
                || channel == null
                || threadTs == null
                || eventTs == null
                || SlackTs.toEpochMicros(eventTs) == null
                || streams == null) {
            log.debug("Slack agent_session_stopped without its fields; ignored");
            return;
        }
        mentorService.handleSessionStop(teamId, channel, user, threadTs, eventTs, streams);
    }

    /** The field as a non-blank string; {@code null} when it is missing, blank, or not a string. */
    private static @Nullable String text(JsonNode event, String field) {
        JsonNode value = event.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    /** The streams as non-blank strings; {@code null} when the field is not an array of them. */
    private static @Nullable List<String> streams(JsonNode value) {
        if (!value.isArray()) {
            return null;
        }
        List<String> streams = new ArrayList<>();
        for (JsonNode stream : value) {
            if (!stream.isString() || stream.asString().isBlank()) {
                return null;
            }
            streams.add(stream.asString());
        }
        return streams;
    }
}
