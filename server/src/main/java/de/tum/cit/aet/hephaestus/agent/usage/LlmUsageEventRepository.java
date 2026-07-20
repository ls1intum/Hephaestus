package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmUsageEventRepository extends JpaRepository<LlmUsageEvent, UUID> {
    List<LlmUsageEvent> findByWorkspaceId(Long workspaceId);

    /**
     * Month-to-date (or any window) BUDGETED spend for one workspace (#1368 slice 6): only
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
     * This workspace's own-provider (BYO) spend for the window — informational only, shown
     * separately from the budgeted total and never summed into it (#1368 slice 6).
     */
    @Query(
        value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.funding_source = 'WORKSPACE'",
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
     * True when at least one INSTANCE-funded event this window has no resolved price (#1368 slice 6).
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
     * Per-job-type breakdown, split the same way the top-level totals are (#1368 slice 6): a
     * budgeted (priced, instance-funded) sum, a separate BYO sum, and an unpriced-event count — never
     * one blind {@code SUM(cost_usd)} mixing funding sources and pricing states.
     */
    @Query(
        value = "SELECT e.job_type AS jobType, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) " +
            "AS pricedTotalCostUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.funding_source = 'WORKSPACE'), 0) AS byoTotalCostUsd, " +
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
     * Per-day breakdown, split the same way as {@link #aggregateByJobType} (#1368 slice 6).
     */
    @Query(
        value = "SELECT (e.occurred_at AT TIME ZONE 'UTC')::date AS day, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) " +
            "AS pricedTotalCostUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.funding_source = 'WORKSPACE'), 0) AS byoTotalCostUsd, " +
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
     * {@link #sumByoCost} do (#1368 slice 6), so the admin list never sums shared + own-provider
     * spend into one figure.
     */
    @Query(
        value = "SELECT w.id AS workspaceId, w.slug AS workspaceSlug, w.display_name AS displayName, " +
            "w.monthly_llm_budget_usd AS monthlyBudgetUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) " +
            "AS pricedTotalCostUsd, " +
            "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.funding_source = 'WORKSPACE'), 0) AS byoTotalCostUsd, " +
            "COALESCE(bool_or(e.pricing_state = 'UNPRICED' AND e.funding_source = 'INSTANCE'), false) " +
            "AS hasUnpricedInstanceUsage, " +
            "COUNT(e.id) AS events " +
            "FROM workspace w LEFT JOIN llm_usage_event e " +
            "ON e.workspace_id = w.id AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "GROUP BY w.id, w.slug, w.display_name, w.monthly_llm_budget_usd ORDER BY pricedTotalCostUsd DESC",
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
        BigDecimal getPricedTotalCostUsd();
        BigDecimal getByoTotalCostUsd();
        boolean isHasUnpricedInstanceUsage();
        long getEvents();
    }
}
