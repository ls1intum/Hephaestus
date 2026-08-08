package de.tum.cit.aet.hephaestus.agent.backfill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * The lifecycle transitions an admin can ask for.
 *
 * <p>{@code RUNNING} is the confirmation: it is the point at which somebody accepts the estimate they
 * were shown and authorises the spend. {@code CANCELLED} stops a campaign for good. Every other
 * transition — pausing on an exhausted budget, resuming when it clears, completing at the end of the
 * scope — belongs to the driver, and is refused here so the state on screen always reflects something
 * the system decided or something a person did, never a mixture.
 */
public record UpdateReviewBackfillRunStatusRequestDTO(
    @NonNull
    @NotNull
    @Schema(
        description = "RUNNING confirms the estimate and starts the campaign; CANCELLED stops it for good",
        allowableValues = { "RUNNING", "CANCELLED" }
    )
    RequestedReviewBackfillStatus status
) {
    public enum RequestedReviewBackfillStatus {
        RUNNING,
        CANCELLED,
    }
}
