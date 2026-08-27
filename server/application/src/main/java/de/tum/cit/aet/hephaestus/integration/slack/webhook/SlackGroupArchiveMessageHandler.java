package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackChannelLifecycleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** A private channel was archived: {@code ACTIVE → PAUSED} (an archived channel receives no further messages). */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackGroupArchiveMessageHandler extends SlackChannelLifecycleMessageHandler {

    public SlackGroupArchiveMessageHandler(
        SlackChannelLifecycleService lifecycleService,
        NatsMessageDeserializer deserializer
    ) {
        super("group_archive", lifecycleService, deserializer);
    }

    @Override
    protected void handleEnvelope(JsonNode root) {
        lifecycleService.onArchived(teamId(root), root.path("event"));
    }
}
