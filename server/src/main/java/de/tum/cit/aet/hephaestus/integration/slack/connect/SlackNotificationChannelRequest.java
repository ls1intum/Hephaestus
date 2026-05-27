package de.tum.cit.aet.hephaestus.integration.slack.connect;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Request body for {@code PATCH /api/v1/workspaces/{workspaceId}/connections/slack/notification-channel}.
 *
 * <p>Both fields are nullable: passing {@code null} clears the corresponding setting.
 */
@Schema(description = "Slack notification-channel configuration. Null fields clear the corresponding value.")
public record SlackNotificationChannelRequest(
    @Schema(description = "Slack channel ID (e.g. C0974LJBPBK). Null clears the channel.") @Nullable String channelId,
    @Schema(description = "Optional team filter label applied to the weekly leaderboard query.")
    @Nullable
    String teamLabel
) {}
