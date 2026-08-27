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

        @NonNull @Schema(description = "Calls this day whose price is not yet known. Excluded from both totals above.")
        Long unpricedEventCount,

        @NonNull Long events) {}
