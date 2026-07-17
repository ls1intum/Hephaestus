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

    /** Month-to-date (or any window) spend for one workspace. Null costs count as zero. */
    @Query(
        value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to",
        nativeQuery = true
    )
    BigDecimal sumCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Events whose cost could not be resolved (unknown model pricing). They sum to zero in
     * {@link #sumCost}, so a capped workspace can spend them unseen — the report surfaces the
     * count so that blind spot is visible rather than silent.
     */
    @Query(
        value = "SELECT COUNT(*) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.cost_usd IS NULL",
        nativeQuery = true
    )
    long countUncosted(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
        value = "SELECT e.job_type AS jobType, COALESCE(SUM(e.cost_usd), 0) AS costUsd, " +
            "SUM(e.input_tokens) AS inputTokens, SUM(e.output_tokens) AS outputTokens, " +
            "SUM(e.cache_read_tokens) AS cacheReadTokens, SUM(e.cache_write_tokens) AS cacheWriteTokens, " +
            "SUM(e.total_calls) AS totalCalls, COUNT(*) AS events " +
            "FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "GROUP BY e.job_type ORDER BY costUsd DESC",
        nativeQuery = true
    )
    List<JobTypeAggregate> aggregateByJobType(
        @Param("workspaceId") Long workspaceId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query(
        value = "SELECT (e.occurred_at AT TIME ZONE 'UTC')::date AS day, COALESCE(SUM(e.cost_usd), 0) AS costUsd, " +
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
     * metadata SQL-side — this stays a metadata-only view (no tenant content).
     */
    @Query(
        value = "SELECT w.id AS workspaceId, w.slug AS workspaceSlug, w.display_name AS displayName, " +
            "w.monthly_llm_budget_usd AS monthlyBudgetUsd, COALESCE(SUM(e.cost_usd), 0) AS costUsd, " +
            "COUNT(e.id) AS events " +
            "FROM workspace w LEFT JOIN llm_usage_event e " +
            "ON e.workspace_id = w.id AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "GROUP BY w.id, w.slug, w.display_name, w.monthly_llm_budget_usd ORDER BY costUsd DESC",
        nativeQuery = true
    )
    List<WorkspaceAggregate> aggregateByWorkspace(@Param("from") Instant from, @Param("to") Instant to);

    interface JobTypeAggregate {
        String getJobType();
        BigDecimal getCostUsd();
        long getInputTokens();
        long getOutputTokens();
        long getCacheReadTokens();
        long getCacheWriteTokens();
        long getTotalCalls();
        long getEvents();
    }

    interface DailyAggregate {
        LocalDate getDay();
        BigDecimal getCostUsd();
        long getEvents();
    }

    interface WorkspaceAggregate {
        Long getWorkspaceId();
        String getWorkspaceSlug();
        String getDisplayName();
        BigDecimal getMonthlyBudgetUsd();
        BigDecimal getCostUsd();
        long getEvents();
    }
}
