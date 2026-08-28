package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackChannelLifecycleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * A public channel was renamed. Heals {@code slack_monitored_channel.channel_name} so the admin UI and audit trail
 * stop showing a stale name. Not a consent transition.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelRenameMessageHandler extends SlackChannelLifecycleMessageHandler {

    public SlackChannelRenameMessageHandler(
        SlackChannelLifecycleService lifecycleService,
        NatsMessageDeserializer deserializer
    ) {
        super("channel_rename", lifecycleService, deserializer);
    }

    @Override
    protected void handleEnvelope(JsonNode root) {
        lifecycleService.onRenamed(teamId(root), root.path("event"));
    }
}
