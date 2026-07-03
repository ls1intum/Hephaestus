package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Admin control-plane view of one allow-listed Slack channel: its discovery identity, its current consent state,
 * and the workspace-wide count of members who have opted out of ingestion (the person firewall). This is the
 * surface the webapp activation panel lists, activates, pauses, revokes, and audits.
 */
@Schema(description = "An allow-listed Slack channel with its consent state and person opt-out count")
public record SlackMonitoredChannelDTO(
    @NonNull @Schema(description = "Internal allow-list row id") Long id,
    @NonNull @Schema(description = "Slack team (workspace) id the channel belongs to") String slackTeamId,
    @NonNull @Schema(description = "Slack channel id (the stable C… id; the natural key)") String slackChannelId,
    @Schema(description = "Human-readable channel name, if known") String channelName,
    @NonNull @Schema(description = "Current per-channel consent lifecycle state") ConsentState consentState,
    @Schema(description = "When the in-channel consent announcement was posted (stamped on first activation)")
    Instant consentAnnouncedAt,
    @NonNull
    @Schema(description = "Number of workspace members who have opted out of ingestion (workspace-wide)")
    Long optedOutMemberCount,
    @NonNull @Schema(description = "When the channel was first discovered / allow-listed") Instant createdAt
) {
    /**
     * Projects a {@link SlackMonitoredChannel} plus the workspace-wide opted-out member count into the DTO.
     *
     * @param channel the allow-list row
     * @param optedOutMemberCount the workspace-wide count of ingestion-opted-out members
     */
    public static SlackMonitoredChannelDTO from(SlackMonitoredChannel channel, long optedOutMemberCount) {
        return new SlackMonitoredChannelDTO(
            channel.getId(),
            channel.getSlackTeamId(),
            channel.getSlackChannelId(),
            channel.getChannelName(),
            channel.getConsentState(),
            channel.getConsentAnnouncedAt(),
            optedOutMemberCount,
            channel.getCreatedAt()
        );
    }
}
