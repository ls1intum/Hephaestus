package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Consumes Slack agent context-change events so the configured Agent View event set is explicit. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackAppContextChangedMessageHandler extends AbstractSlackEnvelopeHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackAppContextChangedMessageHandler.class);

    public SlackAppContextChangedMessageHandler(NatsMessageDeserializer deserializer) {
        super("app_context_changed", deserializer);
    }

    @Override
    protected void handleEnvelope(JsonNode root) {
        JsonNode firstEntity = root.path("event").path("context").path("entities").path(0);
        log.debug(
            "slack.agent: context changed: teamId={}, entityType={}, entityValue={}",
            teamId(root),
            firstEntity.path("type").asString(""),
            firstEntity.path("value").asString("")
        );
    }
}
