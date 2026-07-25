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
        @Nullable @Schema(
            description = "Monthly cap in USD on spend the host pays for (shared models); null = uncapped. Set " +
                "by instance admins only — a workspace admin can see it but not change it."
        ) BigDecimal instanceMonthlyBudgetUsd,
        @Nullable @Schema(
            description = "Monthly cap in USD on spend this workspace pays for through its own connected " +
                "provider; null = uncapped. Set by this workspace's own admins."
        ) BigDecimal byoMonthlyBudgetUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on shared (instance) models, in USD — the figure the " +
                "monthly budget compares against. When unpricedEventCount is non-zero this is a floor, not the " +
                "full total: render it as \"at least $X\"."
        ) BigDecimal pricedTotalCostUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on this workspace's own connected provider(s), in USD — " +
                "the figure byoMonthlyBudgetUsd compares against. Different money from pricedTotalCostUsd: the " +
                "two are never added together."
        ) BigDecimal byoTotalCostUsd,
        @NonNull @Schema(
            description = "Calls this month (any provider) whose price is not yet known. They are excluded from " +
                "both totals above, so a non-zero value means the real spend may be higher than shown."
        ) Long unpricedEventCount,
        @NonNull @Schema(
            description = "Whether host-funded (shared-model) spend is within its cap, has reached it, or can't " +
                "be confirmed because some shared-model usage has no price set."
        ) LlmBudgetVerdict instanceBudgetVerdict,
        @NonNull @Schema(
            description = "The same verdict for spend on this workspace's own provider, against its own cap."
        ) LlmBudgetVerdict byoBudgetVerdict,
        @NonNull @Schema(
            description = "Whether work on SHARED models is currently paused for this workspace. Authoritative — " +
                "it mirrors the live gate rather than being derivable from the verdict alone. Always false for a " +
                "past month, which cannot pause anything."
        ) Boolean instanceFundedPaused,
        @NonNull @Schema(
            description = "Whether work on this workspace's OWN provider is currently paused. The two pause " +
                "independently: an exhausted shared-model budget never stops work the workspace pays for itself."
        ) Boolean byoPaused,
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
            description = "LLM API calls, as reported by the runtime. Detection jobs and mentor turns both " +
                "include every assistant call in an internal tool loop."
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
        @Nullable @Schema(
            description = "Monthly cap in USD on spend this instance pays for; null = uncapped. Yours to set."
        ) BigDecimal instanceMonthlyBudgetUsd,
        @Nullable @Schema(
            description = "The workspace's own cap in USD on its own-provider spend; null = uncapped. Read-only " +
                "here — it governs the workspace's money, so only its own admins may change it."
        ) BigDecimal byoMonthlyBudgetUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on shared (instance) models, in USD — compared against " +
                "instanceMonthlyBudgetUsd."
        ) BigDecimal pricedTotalCostUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on the workspace's own connected provider(s), in USD — " +
                "compared against byoMonthlyBudgetUsd. Different money: never added to pricedTotalCostUsd."
        ) BigDecimal byoTotalCostUsd,
        @NonNull @Schema(description = "Ledger events (jobs / mentor turns) this month, any provider") Long events,
        @NonNull @Schema(
            description = "Whether shared-model spend is within the instance cap, has reached it, or can't be " +
                "confirmed because some shared-model usage has no price set."
        ) LlmBudgetVerdict instanceBudgetVerdict,
        @NonNull @Schema(
            description = "The same verdict for the workspace's own-provider spend against its own cap."
        ) LlmBudgetVerdict byoBudgetVerdict,
        @NonNull @Schema(
            description = "Whether work on SHARED models is paused for this workspace right now (current month " +
                "only) — authoritative, mirroring the live gate."
        ) Boolean instanceFundedPaused,
        @NonNull @Schema(
            description = "Whether work on the workspace's OWN provider is paused right now. The two pause " +
                "independently."
        ) Boolean byoPaused
    ) {}

    @Schema(description = "Set or clear a workspace's monthly LLM budget cap")
    public record UpdateWorkspaceLlmBudgetRequestDTO(
        @Nullable @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) @Schema(
            description = "Budget cap in USD; 0 pauses immediately, null removes the cap"
        ) BigDecimal monthlyLlmBudgetUsd
    ) {}

    @Schema(description = "Set or clear the workspace's own monthly cap on its own-provider spend")
    public record UpdateByoLlmBudgetRequestDTO(
        @Nullable @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) @Schema(
            description = "Cap in USD on this workspace's own-provider spend; 0 pauses that work immediately, " +
                "null removes the cap"
        ) BigDecimal monthlyByoLlmBudgetUsd
    ) {}
}
