package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "A Slack channel the app can offer in the workspace channel picker")
public record SlackChannelCandidateDTO(
    @NonNull @Schema(description = "Stable Slack channel id") String slackChannelId,
    @NonNull @Schema(description = "Current Slack channel name") String channelName,
    @Schema(description = "Whether this is a private channel") boolean privateChannel,
    @Schema(description = "Whether the app bot is already a member") boolean member,
    @Schema(description = "Whether Slack reports the channel as archived") boolean archived,
    @Schema(description = "Existing Hephaestus monitoring state, if already allow-listed") ConsentState consentState
) {}
