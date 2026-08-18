package de.tum.cit.aet.hephaestus.agent.backfill.dto;

import de.tum.cit.aet.hephaestus.agent.backfill.ReviewSweepCadence;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Replace a sweep schedule's terms.
 *
 * <p>Every field is required: a schedule is three numbers that only mean anything together — a cadence
 * changed without its lookback can silently make a window illegal, or leave days nothing ever sweeps.
 * The artifact kind is not among them; a schedule for a different kind of work is a different schedule.
 */
public record UpdateReviewSweepScheduleRequestDTO(
    @NonNull @NotNull @Schema(description = "How often the sweep runs") ReviewSweepCadence cadence,
    @NonNull
    @NotNull
    @Min(1)
    @Max(7)
    @Schema(description = "How far back each sweep looks, in days", example = "2")
    Integer lookbackDays,
    @NonNull
    @NotNull
    @Schema(description = "Whether the scheduler acts on this row; a disabled schedule keeps its terms")
    Boolean enabled
) {}
