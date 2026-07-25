package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * DTOs of the per-workspace LLM usage rollup + monthly budget cap API (#1368).
 *
 * <h2>Two purses, named on one axis</h2>
 *
 * <p>Every figure here belongs to exactly one of two purses: {@code instance*} is spend the host pays
 * for on shared models, {@code ownProvider*} is spend the workspace pays for through its own connected
 * provider. They are never added together. The pair is deliberately named symmetrically —
 * {@code instanceTotalCostUsd}/{@code ownProviderTotalCostUsd},
 * {@code instanceBudgetVerdict}/{@code ownProviderBudgetVerdict} — because the previous naming
 * described the two halves on different axes ({@code priced…} vs {@code byo…}), which read as two
 * unrelated concepts rather than one concept with two owners.
 *
 * <p>"Priced" is gone from the field names for the same reason: BOTH totals exclude usage whose price
 * is not yet known, so qualifying only one of them implied a difference that never existed.
 * {@code unpricedEventCount} states the exclusion once, for both.
 */
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
        ) BigDecimal ownProviderMonthlyBudgetUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on shared (instance) models, in USD — the figure " +
                "instanceMonthlyBudgetUsd compares against. A floor, not the full total, while " +
                "unpricedEventCount is non-zero."
        ) BigDecimal instanceTotalCostUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on this workspace's own connected provider(s), in USD — " +
                "the figure ownProviderMonthlyBudgetUsd compares against. Different money from " +
                "instanceTotalCostUsd: the two are never added together."
        ) BigDecimal ownProviderTotalCostUsd,
        @NonNull @Schema(
            description = "Calls this month (either purse) whose price is not yet known. They are excluded from " +
                "both totals above, so a non-zero value means the real spend may be higher than shown."
        ) Long unpricedEventCount,
        @NonNull @Schema(
            description = "Whether host-funded (shared-model) spend is within its cap, has reached it, or can't " +
                "be confirmed because some shared-model usage has no price set."
        ) LlmBudgetVerdict instanceBudgetVerdict,
        @NonNull @Schema(
            description = "The same verdict for spend on this workspace's own provider, against its own cap."
        ) LlmBudgetVerdict ownProviderBudgetVerdict,
        @NonNull @Schema(
            description = "Whether work on SHARED models is currently paused for this workspace. Authoritative — " +
                "it mirrors the live gate rather than being derivable from the verdict alone. Always false for a " +
                "past month, which cannot pause anything."
        ) Boolean instancePaused,
        @NonNull @Schema(
            description = "Whether work on this workspace's OWN provider is currently paused."
        ) Boolean ownProviderPaused,
        @NonNull List<LlmUsageByJobTypeDTO> byJobType,
        @NonNull List<LlmUsageByDayDTO> byDay,
        @Nullable @Schema(
            description = "Display-only conversion when the instance has a display currency. " +
                "Absent = show USD only."
        ) FxRateInfoDTO fx
    ) {}

    @Schema(description = "Month spend aggregated by job type")
    public record LlmUsageByJobTypeDTO(
        @NonNull LlmUsageJobType jobType,
        @NonNull @Schema(
            description = "Confirmed spend on shared (instance) models for this job type, in USD."
        ) BigDecimal instanceTotalCostUsd,
        @NonNull @Schema(
            description = "Spend on this workspace's own connected provider(s) for this job type, in USD."
        ) BigDecimal ownProviderTotalCostUsd,
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
        ) BigDecimal instanceTotalCostUsd,
        @NonNull @Schema(
            description = "Spend on this workspace's own connected provider(s) for this day, in USD."
        ) BigDecimal ownProviderTotalCostUsd,
        @NonNull @Schema(
            description = "Calls this day whose price is not yet known. Excluded from both totals above."
        ) Long unpricedEventCount,
        @NonNull Long events
    ) {}

    /**
     * The instance-admin cross-tenant rollup for one month.
     *
     * <p>An envelope rather than a bare array because {@code month} and {@code fx} are facts about the
     * REQUEST, not about any workspace in it. They previously rode on every row — one month resolves to
     * exactly one rate, so the value was identical on all N rows and a client had to reach into
     * {@code rows[0]} to find a response-level fact. That is a shape defect, and with the array gone
     * there is now exactly one place each of them can be read from.
     */
    @Schema(description = "Instance-admin per-workspace month rollup (metadata only, no tenant content)")
    public record AdminLlmUsageReportDTO(
        @NonNull @Schema(description = "Calendar month (UTC), ISO yyyy-MM", example = "2026-07") String month,
        @Nullable @Schema(
            description = "Display-only conversion when the instance has a display currency. " +
                "Absent = show USD only. Applies to every USD amount in this response."
        ) FxRateInfoDTO fx,
        @NonNull @Schema(description = "One row per workspace with ledger activity this month") List<
            AdminWorkspaceLlmUsageDTO
        > workspaces
    ) {}

    @Schema(description = "One workspace's month of spend, as an instance admin sees it")
    public record AdminWorkspaceLlmUsageDTO(
        @NonNull @Schema(
            description = "Addresses the workspace everywhere else in the API, including its cap"
        ) String workspaceSlug,
        @NonNull String displayName,
        @Nullable @Schema(
            description = "Monthly cap in USD on spend this instance pays for; null = uncapped. Yours to set."
        ) BigDecimal instanceMonthlyBudgetUsd,
        @Nullable @Schema(
            description = "The workspace's own cap in USD on its own-provider spend; null = uncapped. Read-only " +
                "here — it governs the workspace's money, so only its own admins may change it."
        ) BigDecimal ownProviderMonthlyBudgetUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on shared (instance) models, in USD — compared against " +
                "instanceMonthlyBudgetUsd."
        ) BigDecimal instanceTotalCostUsd,
        @NonNull @Schema(
            description = "This month's confirmed spend on the workspace's own connected provider(s), in USD — " +
                "compared against ownProviderMonthlyBudgetUsd."
        ) BigDecimal ownProviderTotalCostUsd,
        @NonNull @Schema(description = "Ledger events (jobs / mentor turns) this month, either purse") Long events,
        @NonNull @Schema(
            description = "Whether shared-model spend is within the instance cap, has reached it, or can't be " +
                "confirmed because some shared-model usage has no price set."
        ) LlmBudgetVerdict instanceBudgetVerdict,
        @NonNull @Schema(
            description = "The same verdict for the workspace's own-provider spend against its own cap."
        ) LlmBudgetVerdict ownProviderBudgetVerdict,
        @NonNull @Schema(
            description = "Whether work on SHARED models is paused for this workspace right now (current month " +
                "only) — authoritative, mirroring the live gate."
        ) Boolean instancePaused,
        @NonNull @Schema(
            description = "Whether work on the workspace's OWN provider is paused right now."
        ) Boolean ownProviderPaused
    ) {}

    /**
     * The body for BOTH monthly caps: the instance's cap on a workspace
     * ({@code PUT /admin/workspaces/{workspaceSlug}/llm/budget}) and the workspace's cap on its own
     * provider ({@code PUT /workspaces/{workspaceSlug}/llm/budget}).
     *
     * <p>One record, because it is one instrument used by two authorities. Which purse a request
     * governs is already carried by the path it is sent to; encoding it a second time in the field name
     * is what produced two near-identical DTOs whose only real difference was who was allowed to PUT.
     */
    @Schema(description = "Set or clear a monthly LLM budget cap")
    public record UpdateLlmBudgetRequestDTO(
        @Nullable @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) @Schema(
            description = "Cap in USD; 0 pauses the affected work immediately, null removes the cap"
        ) BigDecimal monthlyBudgetUsd
    ) {}
}
