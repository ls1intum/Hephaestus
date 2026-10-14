package de.tum.cit.aet.hephaestus.agent.usage;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.NonNull;

/** One UTC day's slice of a month, inside {@link WorkspaceLlmUsageReportDTO}. */
@Schema(description = "Spend for one UTC day")
public record LlmUsageByDayDTO(
        @NonNull LocalDate day,

        @NonNull @Schema(description = "Confirmed spend on shared (instance) models for this day, in USD.")
        BigDecimal instanceTotalCostUsd,

        @NonNull @Schema(description = "Spend on this workspace's own connected provider(s) for this day, in USD.")
        BigDecimal ownProviderTotalCostUsd,

        @NonNull
        @Schema(
                description =
                        "Runs this day with at least one ledger row whose cost is unavailable because a rate is missing or the measurement is unverified. A "
                                + "practice script row counts toward the run of its review. Those rows are excluded from both "
                                + "totals above.")
        Long unpricedEventCount,

        @NonNull
        @Schema(description = "Runs: job attempts and mentor turns. Rows of models for practice scripts are not runs.")
        Long events) {}
