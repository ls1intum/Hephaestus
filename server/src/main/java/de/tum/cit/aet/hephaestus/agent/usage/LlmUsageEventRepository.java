package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmUsageEventRepository extends JpaRepository<LlmUsageEvent, UUID> {
    /**
     * Idempotent append. A duplicate source attempt is the only write intentionally ignored.
     *
     * <p>The column list, the VALUES list and {@link LlmUsageInsert}'s components are one list written
     * three times; {@code LlmUsageInsertContractTest} checks all three against {@link LlmUsageEvent}
     * position by position, and explains there why this is hand-written SQL rather than a {@code save}.
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO llm_usage_event (
            id, workspace_id, job_type, source_type, source_id, source_attempt, model,
            input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, reasoning_tokens,
            total_calls, cost_usd, occurred_at, pricing_state, funding_source, applied_price_id,
            applied_workspace_model_id, applied_per_1m_input_usd, applied_per_1m_output_usd,
            applied_per_1m_cache_read_usd, applied_per_1m_cache_write_usd
        ) VALUES (
            :#{#event.id()}, :#{#event.workspaceId()}, :#{#event.jobType()}, :#{#event.sourceType()},
            :#{#event.sourceId()}, :#{#event.sourceAttempt()}, :#{#event.model()},
            :#{#event.inputTokens()}, :#{#event.outputTokens()}, :#{#event.cacheReadTokens()},
            :#{#event.cacheWriteTokens()}, :#{#event.reasoningTokens()}, :#{#event.totalCalls()},
            :#{#event.costUsd()}, :#{#event.occurredAt()}, :#{#event.pricingState()},
            :#{#event.fundingSource()}, :#{#event.appliedPriceId()},
            :#{#event.appliedWorkspaceModelId()}, :#{#event.appliedPer1mInputUsd()},
            :#{#event.appliedPer1mOutputUsd()}, :#{#event.appliedPer1mCacheReadUsd()},
            :#{#event.appliedPer1mCacheWriteUsd()}
        ) ON CONFLICT (source_type, source_id, source_attempt) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(@Param("event") LlmUsageInsert event);

    /**
     * Month-to-date (or any window) BUDGETED spend for one workspace: only
     * instance-funded, confirmed-priced events count — this is the sum the monthly budget cap
     * compares against. A workspace's own BYO (bring-your-own) spend NEVER counts toward its
     * instance-set budget (see {@link #sumByoCost}), and an UNPRICED event contributes nothing
     * (see {@link #existsUnpricedInstanceFunded} for the "verdict can't be trusted" signal).
     */
    @Query(
        value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'",
        nativeQuery = true
    )
    BigDecimal sumCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * This workspace's confirmed own-provider (BYO) spend for the window — what its own cap is
     * measured against, and never summed into the instance-funded total (the two are different
     * people's money).
     *
     * <p>The {@code pricing_state = 'PRICED'} predicate is explicit rather than implied by
     * "UNPRICED rows have a NULL cost": this sum now decides whether a workspace is paused, so the
     * rule it enforces has to be readable in the query rather than inferred from a column's
     * nullability. Unpriced BYO usage is reported through
     * {@link #existsUnpricedWorkspaceFunded} instead, exactly as its instance-funded mirror.
     */
    @Query(
        value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'",
        nativeQuery = true
    )
    BigDecimal sumByoCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Events whose cost could not be resolved (unknown model pricing), any funding source. They
     * contribute nothing to {@link #sumCost} / {@link #sumByoCost}, so the report surfaces the
     * count so that blind spot is visible rather than silent.
     */
    @Query(
        value = "SELECT COUNT(*) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.cost_usd IS NULL",
        nativeQuery = true
    )
    long countUncosted(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * True when at least one INSTANCE-funded event this window has no resolved price.
     * This is what turns a budget verdict from WITHIN into UNVERIFIABLE — spend that could push the
     * workspace over its cap but that {@link #sumCost} cannot see.
     */
    @Query(
        value = "SELECT EXISTS(SELECT 1 FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.pricing_state = 'UNPRICED' AND e.funding_source = 'INSTANCE')",
        nativeQuery = true
    )
    boolean existsUnpricedInstanceFunded(
        @Param("workspaceId") Long workspaceId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /**
     * The BYO mirror of {@link #existsUnpricedInstanceFunded}: at least one own-provider event this
     * window has no resolved price, so {@link #sumByoCost} cannot prove the workspace is under its
     * own cap.
     *
     * <p>Only the workspace admin can fix this one — BYO prices are set on the workspace's own model
     * (see {@code WorkspaceLlmModel}), which is why an unpriced BYO model may pause BYO work but
     * never instance-funded work, and vice versa. Each cap is only ever blocked by a blind spot its
     * own owner can clear.
     */
    @Query(
        value = "SELECT EXISTS(SELECT 1 FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.pricing_state = 'UNPRICED' AND e.funding_source = 'WORKSPACE')",
        nativeQuery = true
    )
    boolean existsUnpricedWorkspaceFunded(
        @Param("workspaceId") Long workspaceId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /**
     * Per-job-type breakdown, split the same way the top-level totals are: a
     * budgeted (priced, instance-funded) sum, a separate BYO sum, and an unpriced-event count — never
     * one blind {@code SUM(cost_usd)} mixing funding sources and pricing states.
     */
    @Query(
        value = "SELECT e.job_type AS jobType, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) " +
            "AS pricedTotalCostUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'), 0) " +
            "AS byoTotalCostUsd, " +
            "COUNT(*) FILTER (WHERE e.cost_usd IS NULL) AS unpricedEventCount, " +
            "SUM(e.input_tokens) AS inputTokens, SUM(e.output_tokens) AS outputTokens, " +
            "SUM(e.cache_read_tokens) AS cacheReadTokens, SUM(e.cache_write_tokens) AS cacheWriteTokens, " +
            "SUM(e.total_calls) AS totalCalls, COUNT(*) AS events " +
            "FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "GROUP BY e.job_type ORDER BY pricedTotalCostUsd DESC",
        nativeQuery = true
    )
    List<JobTypeAggregate> aggregateByJobType(
        @Param("workspaceId") Long workspaceId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /**
     * Per-day breakdown, split the same way as {@link #aggregateByJobType}.
     */
    @Query(
        value = "SELECT (e.occurred_at AT TIME ZONE 'UTC')::date AS day, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) " +
            "AS pricedTotalCostUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'), 0) " +
            "AS byoTotalCostUsd, " +
            "COUNT(*) FILTER (WHERE e.cost_usd IS NULL) AS unpricedEventCount, " +
            "COUNT(*) AS events " +
            "FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "GROUP BY day ORDER BY day",
        nativeQuery = true
    )
    List<DailyAggregate> aggregateByDay(
        @Param("workspaceId") Long workspaceId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /**
     * Instance-admin cross-tenant rollup: one row per workspace (workspaces without spend
     * included via LEFT JOIN so the admin sees budgets even at zero usage). Joins workspace
     * metadata SQL-side — this stays a metadata-only view (no tenant content). Splits budgeted
     * (instance-funded, priced) spend from BYO spend the same way {@link #sumCost} /
     * {@link #sumByoCost} do, so the admin list never sums shared + own-provider
     * spend into one figure.
     *
     * <p><b>Unpaged on purpose.</b> The row count is the instance's workspace count, not its event
     * count — a self-hosted Hephaestus is a university or a company, so this is tens to low hundreds
     * of rows of five scalars each, aggregated in one indexed pass over the month window. Paging it
     * would also break the page: the admin's question is "who is spending", which needs the whole
     * month ranked before it can be truncated. Revisit if an instance ever passes ~10k workspaces —
     * at which point the fix is a materialised monthly rollup, not a page cursor.
     */
    @Query(
        value = "SELECT w.id AS workspaceId, w.slug AS workspaceSlug, w.display_name AS displayName, " +
            "w.monthly_llm_budget_usd AS monthlyBudgetUsd, " +
            "w.monthly_byo_llm_budget_usd AS byoMonthlyBudgetUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) " +
            "AS pricedTotalCostUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'), 0) " +
            "AS byoTotalCostUsd, " +
            "COALESCE(BOOL_OR(e.pricing_state = 'UNPRICED' AND e.funding_source = 'WORKSPACE'), false) " +
            "AS hasUnpricedByoUsage, " +
            "COALESCE(bool_or(e.pricing_state = 'UNPRICED' AND e.funding_source = 'INSTANCE'), false) " +
            "AS hasUnpricedInstanceUsage, " +
            "COUNT(e.id) AS events " +
            "FROM workspace w LEFT JOIN llm_usage_event e " +
            "ON e.workspace_id = w.id AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "GROUP BY w.id, w.slug, w.display_name, w.monthly_llm_budget_usd, w.monthly_byo_llm_budget_usd " +
            "ORDER BY pricedTotalCostUsd DESC",
        nativeQuery = true
    )
    List<WorkspaceAggregate> aggregateByWorkspace(@Param("from") Instant from, @Param("to") Instant to);

    interface JobTypeAggregate {
        String getJobType();
        BigDecimal getPricedTotalCostUsd();
        BigDecimal getByoTotalCostUsd();
        long getUnpricedEventCount();
        long getInputTokens();
        long getOutputTokens();
        long getCacheReadTokens();
        long getCacheWriteTokens();
        long getTotalCalls();
        long getEvents();
    }

    interface DailyAggregate {
        LocalDate getDay();
        BigDecimal getPricedTotalCostUsd();
        BigDecimal getByoTotalCostUsd();
        long getUnpricedEventCount();
        long getEvents();
    }

    interface WorkspaceAggregate {
        Long getWorkspaceId();
        String getWorkspaceSlug();
        String getDisplayName();
        BigDecimal getMonthlyBudgetUsd();
        BigDecimal getByoMonthlyBudgetUsd();
        BigDecimal getPricedTotalCostUsd();
        BigDecimal getByoTotalCostUsd();
        boolean isHasUnpricedInstanceUsage();
        boolean isHasUnpricedByoUsage();
        long getEvents();
    }
}
