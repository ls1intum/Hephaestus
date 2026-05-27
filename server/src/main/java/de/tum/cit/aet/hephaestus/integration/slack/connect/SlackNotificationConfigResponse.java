package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Response for the Slack-notification admin endpoints. Carries the four fields that
 * are user-meaningful: team identity (from OAuth) + notification channel (user-set).
 */
@Schema(description = "Slack-connection notification configuration projection.")
public record SlackNotificationConfigResponse(
    @Nullable String teamId,
    @Nullable String teamName,
    @Nullable String notificationChannelId,
    @Nullable String teamLabel
) {
    public static SlackNotificationConfigResponse from(ConnectionConfig.SlackConfig config) {
        return new SlackNotificationConfigResponse(
            config.teamId(),
            config.teamName(),
            config.notificationChannelId(),
            config.teamLabel()
        );
    }
}
