package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEvent;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/** One immutable audit entry in a Slack channel's consent-transition history (who / when / from → to / why). */
@Schema(description = "An immutable Slack channel consent-transition audit entry")
public record SlackChannelConsentEventDTO(
    @NonNull @Schema(description = "Audit entry id") Long id,
    @NonNull @Schema(description = "Slack channel id the transition applied to") String slackChannelId,
    @Schema(description = "State the channel left (absent for the very first record)") ConsentState fromState,
    @NonNull @Schema(description = "State the channel entered") ConsentState toState,
    @Schema(description = "Workspace user id of the admin who made the change") Long actorUserId,
    @Schema(description = "Optional free-text reason the admin supplied") String reason,
    @NonNull @Schema(description = "When the transition was recorded") Instant createdAt
) {
    public static SlackChannelConsentEventDTO from(SlackChannelConsentEvent event) {
        return new SlackChannelConsentEventDTO(
            event.getId(),
            event.getSlackChannelId(),
            event.getFromState(),
            event.getToState(),
            event.getActorUserId(),
            event.getReason(),
            event.getCreatedAt()
        );
    }
}
