package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackAssistantEventHandler;
import de.tum.cit.aet.hephaestus.integration.slack.onboarding.SlackAppHomeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Renders Slack App Home from the unified Slack event stream. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackAppHomeOpenedMessageHandler extends AbstractSlackEnvelopeHandler {

    private final SlackAppHomeService appHomeService;
    private final SlackAssistantEventHandler assistantEventHandler;

    public SlackAppHomeOpenedMessageHandler(
        SlackAppHomeService appHomeService,
        SlackAssistantEventHandler assistantEventHandler,
        NatsMessageDeserializer deserializer
    ) {
        super("app_home_opened", deserializer);
        this.appHomeService = appHomeService;
        this.assistantEventHandler = assistantEventHandler;
    }

    @Override
    protected void handleEnvelope(JsonNode root) {
        JsonNode event = root.path("event");
        String tab = event.path("tab").asString("home");
        String teamId = teamId(root);
        if ("messages".equals(tab)) {
            assistantEventHandler.onMessagesOpened(teamId, event);
        } else if ("home".equals(tab)) {
            String slackUserId = event.path("user").asString("");
            appHomeService.onHomeOpened(teamId, slackUserId);
        }
    }
}
