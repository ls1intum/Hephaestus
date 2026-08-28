package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WebhookPublishGate;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackChannelConsentGate;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackWorkspaceResolver;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackChannelMessagePublishGate implements WebhookPublishGate {

    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackChannelConsentGate consentGate;
    private final boolean conversationIngestEnabled;

    public SlackChannelMessagePublishGate(
        SlackWorkspaceResolver workspaceResolver,
        SlackChannelConsentGate consentGate,
        @Value("${hephaestus.integration.slack.conversation-ingest.enabled:true}") boolean conversationIngestEnabled
    ) {
        this.workspaceResolver = workspaceResolver;
        this.consentGate = consentGate;
        this.conversationIngestEnabled = conversationIngestEnabled;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    @Transactional(readOnly = true)
    public Decision evaluate(JsonNode payload, Map<String, String> headers) {
        JsonNode event = payload.path("event");
        if (!isChannelMessage(event)) {
            return Decision.allow();
        }
        if (!conversationIngestEnabled) {
            return Decision.drop("slack-channel-ingest-disabled");
        }
        // A delete must reach the tombstone in EVERY consent state: a message stored while the channel was ACTIVE
        // still exists during PAUSED, and the author's deletion has to erase our copy too (GDPR parity). The
        // tombstone is contentless, so letting it through never stores new content.
        if ("message_deleted".equals(event.path("subtype").asString(""))) {
            return Decision.allow();
        }
        String teamId = teamId(payload);
        String channelId = event.path("channel").asString("");
        if (teamId.isBlank() || channelId.isBlank()) {
            return Decision.drop("slack-channel-message-missing-team-or-channel");
        }
        return workspaceResolver
            .resolveWorkspaceId(teamId)
            .filter(workspaceId -> consentGate.ingestAllowed(workspaceId, channelId))
            .map(_workspaceId -> Decision.allow())
            .orElseGet(() -> Decision.drop("slack-channel-not-active"));
    }

    private static boolean isChannelMessage(JsonNode event) {
        if (!"message".equals(event.path("type").asString(""))) {
            return false;
        }
        String channelType = event.path("channel_type").asString("");
        return "channel".equals(channelType) || "group".equals(channelType);
    }

    private static String teamId(JsonNode root) {
        String team = root.path("team_id").asString("");
        if (!team.isBlank()) {
            return team;
        }
        return root.path("authorizations").path(0).path("team_id").asString("");
    }
}
