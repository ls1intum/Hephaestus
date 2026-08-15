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
     * Idempotent append. The column list, the VALUES list and {@link LlmUsageInsert}'s components are
     * one list written three times, checked against each other position by position by
     * {@code LlmUsageInsertContractTest}.
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

    /** The sum the instance cap compares against. Own-provider spend is a different purse. */
    @Query(
        value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'",
        nativeQuery = true
    )
    BigDecimal sumCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * The sum the workspace's own-provider cap compares against — never summed into the instance total,
     * the two are different people's money.
     */
    @Query(
        value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'",
        nativeQuery = true
    )
    BigDecimal sumByoCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /** Events either sum leaves out because no price could be resolved, so the blind spot is visible. */
    @Query(
        value = "SELECT COUNT(*) FROM llm_usage_event e " +
            "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to " +
            "AND e.cost_usd IS NULL",
        nativeQuery = true
    )
    long countUncosted(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /** What turns the instance verdict from WITHIN into UNVERIFIABLE: spend {@link #sumCost} cannot see. */
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
     * The own-provider mirror of {@link #existsUnpricedInstanceFunded}. Kept separate so each cap is
     * only ever blocked by a blind spot its own owner can clear.
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
     * Instance-admin cross-tenant rollup: every workspace, including those with no spend this window.
     * Unpaged — the row count is the instance's workspace count, and ranking by spend needs the whole
     * month anyway.
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

    /**
     * Mean cost of one <em>review</em> of this job type, over reviews this window could price in full.
     *
     * <p>Deliberately not derived from {@link #aggregateByJobType}: a row there is one <em>attempt</em>,
     * not one review, so dividing spend by attempt count under-quotes exactly the retry-heavy workspaces
     * whose campaigns cost the most. Grouping by source first makes "reviews" the denominator.
     *
     * <p>A review with any unpriced attempt is dropped from numerator and denominator together, so a
     * half-priced catalogue reports the mean of what it could price rather than one dragged toward zero.
     */
    @Query(
        value = """
        SELECT COALESCE(SUM(r.review_cost), 0) AS totalCostUsd, COUNT(*) AS reviews
        FROM (
            SELECT e.source_type, e.source_id, SUM(e.cost_usd) AS review_cost
            FROM llm_usage_event e
            WHERE e.workspace_id = :workspaceId AND e.job_type = :jobType
              AND e.occurred_at >= :from AND e.occurred_at < :to
            GROUP BY e.source_type, e.source_id
            HAVING COUNT(*) FILTER (WHERE e.cost_usd IS NULL) = 0
        ) r
        """,
        nativeQuery = true
    )
    ReviewCostAggregate aggregateCostPerReview(
        @Param("workspaceId") Long workspaceId,
        @Param("jobType") String jobType,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /** Both purses summed: a forecast is about the work, not about who pays for it. */
    interface ReviewCostAggregate {
        BigDecimal getTotalCostUsd();
        long getReviews();
    }

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
