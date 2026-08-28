package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.exception.PayloadParsingException;
import io.nats.client.Message;
import java.io.IOException;
import tools.jackson.databind.JsonNode;

abstract class AbstractSlackEnvelopeHandler implements IntegrationMessageHandler {

    private final EventTypeKey key;
    private final NatsMessageDeserializer deserializer;

    AbstractSlackEnvelopeHandler(String eventType, NatsMessageDeserializer deserializer) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        this.key = new EventTypeKey(IntegrationKind.SLACK, eventType);
        this.deserializer = deserializer;
    }

    @Override
    public EventTypeKey key() {
        return key;
    }

    @Override
    public void onMessage(Message msg) {
        JsonNode root;
        try {
            root = deserializer.deserialize(msg, JsonNode.class);
        } catch (IOException e) {
            throw new PayloadParsingException(
                "Payload parsing failed for Slack subject: " + sanitizeForLog(msg.getSubject()),
                e
            );
        }
        handleEnvelope(root);
    }

    protected abstract void handleEnvelope(JsonNode root);

    protected static String teamId(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        String team = root.path("team_id").asString("");
        if (!team.isBlank()) {
            return team;
        }
        return root.path("authorizations").path(0).path("team_id").asString("");
    }
}
