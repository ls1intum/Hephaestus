package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

/**
 * Request to transition a Slack channel to a target consent state. The transition is a guarded, idempotent switch:
 * {@code PENDING → ACTIVE} (posts the in-channel announcement + starts forward-only ingestion),
 * {@code ACTIVE ⇄ PAUSED} (stop/resume, data kept), {@code * → REVOKED} (stop + erase). An illegal edge is a 409.
 */
@Schema(description = "Transition a Slack channel to a target consent state")
public record UpdateSlackChannelConsentRequestDTO(
    @NonNull
    @NotNull
    @Schema(description = "Target consent state", requiredMode = Schema.RequiredMode.REQUIRED)
    ConsentState consentState,

    @Size(max = 2000)
    @Schema(description = "Optional free-text reason recorded in the immutable audit trail")
    String reason
) {}
