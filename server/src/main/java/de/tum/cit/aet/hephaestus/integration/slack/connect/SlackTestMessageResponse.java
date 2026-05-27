package de.tum.cit.aet.hephaestus.integration.slack.connect;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Result of {@code POST /api/v1/workspaces/{workspaceId}/connections/slack/test-message}.
 *
 * <p>{@code ok=true} carries the channel id that received the test post. {@code ok=false}
 * carries the Slack error code ({@code channel_not_found}, {@code not_in_channel}, …) so
 * the admin UI can render a meaningful message.
 */
@Schema(description = "Result of a Slack test-message dispatch.")
public record SlackTestMessageResponse(boolean ok, @Nullable String channelId, @Nullable String slackError) {}
