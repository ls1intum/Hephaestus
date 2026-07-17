package de.tum.cit.aet.hephaestus.agent.usage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** DTOs of the per-workspace LLM usage rollup + monthly budget cap API (#1368). */
public final class LlmUsageDTOs {

    private LlmUsageDTOs() {}

    @Schema(description = "One calendar month of a workspace's LLM spend, rolled up from the usage ledger")
    public record WorkspaceLlmUsageReportDTO(
        @NonNull @Schema(description = "Calendar month (UTC), ISO yyyy-MM", example = "2026-07") String month,
        @Nullable @Schema(description = "Monthly budget cap in USD; null = uncapped") BigDecimal monthlyBudgetUsd,
        @NonNull @Schema(description = "Total spend in the month, USD") BigDecimal totalCostUsd,
        @NonNull @Schema(
            description = "Spend reached the cap — detection and mentor turns are paused for the current month"
        ) Boolean overBudget,
        @NonNull @Schema(
            description = "Events in the month whose cost could not be resolved (unknown model pricing). " +
                "They count as zero spend, so a non-zero value means the budget cap is not seeing everything — " +
                "add a model_pricing row for the model."
        ) Long uncostedEvents,
        @NonNull List<LlmUsageByJobTypeDTO> byJobType,
        @NonNull List<LlmUsageByDayDTO> byDay
    ) {}

    @Schema(description = "Month spend aggregated by job type")
    public record LlmUsageByJobTypeDTO(
        @NonNull LlmUsageJobType jobType,
        @NonNull BigDecimal costUsd,
        @NonNull Long inputTokens,
        @NonNull Long outputTokens,
        @NonNull Long cacheReadTokens,
        @NonNull Long cacheWriteTokens,
        @NonNull @Schema(
            description = "LLM API calls, as reported by the runtime. Detection jobs report their real call " +
                "count; a mentor turn reports 1 per turn regardless of its internal tool loop, so compare turns " +
                "to turns, not to job calls."
        ) Long totalCalls,
        @NonNull @Schema(description = "Ledger events (jobs / mentor turns)") Long events
    ) {}

    @Schema(description = "Spend for one UTC day")
    public record LlmUsageByDayDTO(@NonNull LocalDate day, @NonNull BigDecimal costUsd, @NonNull Long events) {}

    @Schema(description = "Instance-admin per-workspace month rollup (metadata only, no tenant content)")
    public record AdminWorkspaceLlmUsageDTO(
        @NonNull Long workspaceId,
        @NonNull String workspaceSlug,
        @NonNull String displayName,
        @Nullable BigDecimal monthlyBudgetUsd,
        @NonNull BigDecimal costUsd,
        @NonNull Long events,
        @NonNull Boolean overBudget
    ) {}

    @Schema(description = "Set or clear a workspace's monthly LLM budget cap")
    public record UpdateWorkspaceLlmBudgetRequestDTO(
        @Nullable @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) @Schema(
            description = "Budget cap in USD; 0 pauses immediately, null removes the cap"
        ) BigDecimal monthlyLlmBudgetUsd
    ) {}
}
