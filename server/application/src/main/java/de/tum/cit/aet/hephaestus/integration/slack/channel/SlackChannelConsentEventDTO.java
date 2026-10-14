package de.tum.cit.aet.hephaestus.integration.slack.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackChannelConsentEvent;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "An immutable Slack channel consent-transition audit entry")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SlackChannelConsentEventDTO(
        @NonNull
        @Schema(
                description = "Audit entry id",
                type = "string",
                pattern = "^[1-9][0-9]*$",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @NonNull @Schema(description = "Slack channel id the transition applied to")
        String slackChannelId,

        @Schema(description = "State the channel left (absent for the very first record)") @Nullable
        ConsentState fromState,

        @NonNull @Schema(description = "State the channel entered")
        ConsentState toState,

        @Schema(
                description = "Account id of the admin who made the change",
                type = "string",
                pattern = "^[1-9][0-9]*$",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Nullable
        String actorAccountId,

        @Schema(description = "Optional free-text reason the admin supplied") @Nullable
        String reason,

        @NonNull @Schema(description = "When the transition was recorded")
        Instant createdAt) {
    public static SlackChannelConsentEventDTO from(SlackChannelConsentEvent event) {
        return new SlackChannelConsentEventDTO(
                Objects.requireNonNull(event.getId(), "Persisted consent event must have an id")
                        .toString(),
                event.getSlackChannelId(),
                event.getFromState(),
                event.getToState(),
                event.getActorAccountId() == null
                        ? null
                        : event.getActorAccountId().toString(),
                event.getReason(),
                Objects.requireNonNull(event.getCreatedAt(), "Persisted consent event must have a creation time"));
    }
}
