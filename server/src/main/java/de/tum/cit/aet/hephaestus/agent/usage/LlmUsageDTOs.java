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
        @NonNull @Schema(
            description = "This month's confirmed spend on shared (instance) models, in USD — the figure the " +
                "monthly budget compares against. When unpricedEventCount is non-zero this is a floor, not the " +
                "full total: render it as \"at least $X\"."
        ) BigDecimal pricedTotalCostUsd,
        @NonNull @Schema(
            description = "This month's spend on this workspace's own connected provider(s), in USD. Shown " +
                "separately — it never counts toward the monthly budget and must never be added to " +
                "pricedTotalCostUsd."
        ) BigDecimal byoTotalCostUsd,
        @NonNull @Schema(
            description = "Calls this month (any provider) whose price is not yet known. They are excluded from " +
                "both totals above, so a non-zero value means the real spend may be higher than shown."
        ) Long unpricedEventCount,
        @NonNull @Schema(
            description = "Whether this month's confirmed spend is within the cap, has reached it (work is " +
                "paused), or can't be fully confirmed yet because some usage above has no price set."
        ) LlmBudgetVerdict verdict,
        @NonNull @Schema(
            description = "Whether new AI work is currently paused for this workspace by this server's budget " +
                "policy — true when the cap is reached (verdict=EXHAUSTED), or when verdict=UNVERIFIABLE AND " +
                "this server's unpriced-usage policy is BLOCK (the default WARN policy never pauses on " +
                "UNVERIFIABLE alone). Authoritative: the webapp cannot derive this from verdict alone because " +
                "it doesn't know the instance's unpriced-usage policy."
        ) Boolean usagePaused,
        @NonNull List<LlmUsageByJobTypeDTO> byJobType,
        @NonNull List<LlmUsageByDayDTO> byDay
    ) {}

    @Schema(description = "Month spend aggregated by job type")
    public record LlmUsageByJobTypeDTO(
        @NonNull LlmUsageJobType jobType,
        @NonNull @Schema(
            description = "Confirmed spend on shared (instance) models for this job type, in USD."
        ) BigDecimal pricedTotalCostUsd,
        @NonNull @Schema(
            description = "Spend on this workspace's own connected provider(s) for this job type, in USD. Never " +
                "counts toward the monthly budget."
        ) BigDecimal byoTotalCostUsd,
        @NonNull @Schema(
            description = "Calls for this job type whose price is not yet known. Excluded from both totals above."
        ) Long unpricedEventCount,
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
    public record LlmUsageByDayDTO(
        @NonNull LocalDate day,
        @NonNull @Schema(
            description = "Confirmed spend on shared (instance) models for this day, in USD."
        ) BigDecimal pricedTotalCostUsd,
        @NonNull @Schema(
            description = "Spend on this workspace's own connected provider(s) for this day, in USD. Never " +
                "counts toward the monthly budget."
        ) BigDecimal byoTotalCostUsd,
        @NonNull @Schema(
            description = "Calls this day whose price is not yet known. Excluded from both totals above."
        ) Long unpricedEventCount,
        @NonNull Long events
    ) {}

    @Schema(description = "Instance-admin per-workspace month rollup (metadata only, no tenant content)")
    public record AdminWorkspaceLlmUsageDTO(
        @NonNull Long workspaceId,
        @NonNull String workspaceSlug,
        @NonNull String displayName,
        @Nullable BigDecimal monthlyBudgetUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on shared (instance) models, in USD — compared against " +
                "the budget cap above."
        ) BigDecimal pricedTotalCostUsd,
        @NonNull @Schema(
            description = "This month's spend on the workspace's own connected provider(s), in USD. Never counts " +
                "toward the budget cap."
        ) BigDecimal byoTotalCostUsd,
        @NonNull @Schema(description = "Ledger events (jobs / mentor turns) this month, any provider") Long events,
        @NonNull @Schema(
            description = "Whether this month's confirmed spend is within the cap, has reached it, or can't be " +
                "fully confirmed yet because some usage has no price set."
        ) LlmBudgetVerdict verdict
    ) {}

    @Schema(description = "Set or clear a workspace's monthly LLM budget cap")
    public record UpdateWorkspaceLlmBudgetRequestDTO(
        @Nullable @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) @Schema(
            description = "Budget cap in USD; 0 pauses immediately, null removes the cap"
        ) BigDecimal monthlyLlmBudgetUsd
    ) {}
}
