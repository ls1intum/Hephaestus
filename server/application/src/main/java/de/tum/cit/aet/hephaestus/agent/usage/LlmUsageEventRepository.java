package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmUsageEventRepository extends JpaRepository<LlmUsageEvent, UUID> {
    /** One retention batch. {@code occurred_at} is indexed on its own so this never scans the ledger. */
    @WorkspaceAgnostic("Retention removes expired usage rows across all workspaces")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "DELETE FROM llm_usage_event WHERE id IN ("
                    + "  SELECT e.id FROM llm_usage_event e WHERE e.occurred_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteExpired(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    /**
     * Idempotent append. The column list, the VALUES list and {@link LlmUsageInsert}'s components are
     * one list written three times, checked against each other position by position by
     * {@code LlmUsageInsertContractTest}.
     */
    @Modifying
    @Query(value = """
        INSERT INTO llm_usage_event (
            id, workspace_id, job_type, source_type, source_id, source_attempt, model,
            input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, reasoning_tokens,
            total_calls, cost_usd, occurred_at, pricing_state, funding_source, applied_price_id,
            applied_workspace_model_id, applied_per_1m_input_usd, applied_per_1m_output_usd,
            applied_per_1m_cache_read_usd, applied_per_1m_cache_write_usd, usage_provenance
        ) VALUES (
            :#{#event.id()}, :#{#event.workspaceId()}, :#{#event.jobType()}, :#{#event.sourceType()},
            :#{#event.sourceId()}, :#{#event.sourceAttempt()}, :#{#event.model()},
            :#{#event.inputTokens()}, :#{#event.outputTokens()}, :#{#event.cacheReadTokens()},
            :#{#event.cacheWriteTokens()}, :#{#event.reasoningTokens()}, :#{#event.totalCalls()},
            :#{#event.costUsd()}, :#{#event.occurredAt()}, :#{#event.pricingState()},
            :#{#event.fundingSource()}, :#{#event.appliedPriceId()},
            :#{#event.appliedWorkspaceModelId()}, :#{#event.appliedPer1mInputUsd()},
            :#{#event.appliedPer1mOutputUsd()}, :#{#event.appliedPer1mCacheReadUsd()},
            :#{#event.appliedPer1mCacheWriteUsd()}, :#{#event.usageProvenance()}
        ) ON CONFLICT (source_type, source_id, source_attempt) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("event") LlmUsageInsert event);

    /** The sum the instance cap compares against. Own-provider spend is a different purse. */
    @Query(
            value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "AND e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'",
            nativeQuery = true)
    BigDecimal sumCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * The sum the workspace's own-provider cap compares against — never summed into the instance total,
     * the two are different people's money.
     */
    @Query(
            value = "SELECT COALESCE(SUM(e.cost_usd), 0) FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "AND e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'",
            nativeQuery = true)
    BigDecimal sumByoCost(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Events either sum leaves out because no price could be resolved, so the blind spot is visible.
     *
     * <p>Reads {@code pricing_state}, not {@code cost_usd IS NULL}. The two are meant to say the same
     * thing and the application only ever writes rows where they do, but the 1785015307013 ledger
     * backfill classified a zero-cost row with real tokens as UNPRICED while storing the zero, so those
     * rows blocked every job in the month and this count did not mention them: an operator read "1
     * unpriced event" off a month that had nineteen holding the cap shut. The state column is the
     * arbiter because it is what {@link #existsUnpricedInstanceFunded} — the thing that actually blocks —
     * reads. The migration repairs the rows and a CHECK constraint now stops the two from parting again;
     * this query stops depending on that constraint holding.
     */
    @Query(
            value = "SELECT COUNT(*) FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "AND e.pricing_state = 'UNPRICED'",
            nativeQuery = true)
    long countUncosted(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Every UNPRICED row in the window, across all workspaces — the repricing pass's work list.
     *
     * <p>Ordered oldest first so a backlog is cleared in the order it accumulated, and paged because one
     * catalogue gap can leave a month's worth of rows behind it.
     */
    @WorkspaceAgnostic("Repricing pass enumerates the instance's unpriced spend across all tenants")
    @Query("SELECT new de.tum.cit.aet.hephaestus.agent.usage.UnpricedLedgerRow("
            + "e.id, e.workspace.id, e.model, e.fundingSource, e.inputTokens, e.outputTokens, "
            + "e.cacheReadTokens, e.cacheWriteTokens, e.appliedPriceId, e.appliedWorkspaceModelId) "
            + "FROM LlmUsageEvent e "
            + "WHERE e.occurredAt >= :from AND e.occurredAt < :to "
            + "AND e.pricingState = de.tum.cit.aet.hephaestus.agent.usage.PricingState.UNPRICED "
            + "ORDER BY e.occurredAt")
    List<UnpricedLedgerRow> findUnpricedInWindow(
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * The one sanctioned UPDATE on the ledger: fill in a price that could not be resolved when the row
     * was written. See {@link LlmUsageRepricer} for why this is a repair rather than a rewrite.
     *
     * <p>Fenced on {@code pricing_state = 'UNPRICED'}, so it can only ever move a row out of the
     * unpriced state and never re-price one that already carries a frozen amount somebody was charged.
     * Two pods repricing the same row concurrently is therefore safe: the loser updates nothing.
     *
     * <p>Every token column is untouched. The measurement is not in question here; only the arithmetic
     * over it was impossible.
     *
     * <p><b>The price travels as one value.</b> The eight columns this writes are the eight components
     * of the {@link LlmPriceSnapshot} the repricer resolved, so it is passed as the snapshot rather than
     * unpacked into a positional list: a row is priced by <em>one</em> catalogue entry, and splitting it
     * into separate arguments only creates the chance of pairing one entry's rate with another's id.
     * Same shape as {@link #insertIfAbsent}, and for the same reason.
     *
     * <p>{@link LlmPriceSnapshot#fundingSource()} is deliberately not in the SET list. Which purse paid
     * for an attempt was settled when it was admitted; a reprice recovers an amount, it never moves a
     * charge between the instance's money and the workspace's.
     *
     * @param price the resolved price, whose {@code pricingState} is what the row moves to — always the
     *     state the caller computed, so a NO_CHARGE model settles as a real zero rather than staying a
     *     blind spot
     * @return 1 when this call priced the row, 0 when it was no longer unpriced
     */
    @WorkspaceAgnostic("ID-based price repair; the event id comes from the cross-tenant repricing enumeration")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "UPDATE llm_usage_event SET cost_usd = :costUsd, "
                    + "pricing_state = :#{#price.pricingState().name()}, "
                    + "applied_price_id = :#{#price.appliedPriceId()}, "
                    + "applied_workspace_model_id = :#{#price.appliedWorkspaceModelId()}, "
                    + "applied_per_1m_input_usd = :#{#price.per1mInputUsd()}, "
                    + "applied_per_1m_output_usd = :#{#price.per1mOutputUsd()}, "
                    + "applied_per_1m_cache_read_usd = :#{#price.per1mCacheReadUsd()}, "
                    + "applied_per_1m_cache_write_usd = :#{#price.per1mCacheWriteUsd()} "
                    + "WHERE id = :id AND pricing_state = 'UNPRICED'",
            nativeQuery = true)
    int applyResolvedPrice(
            @Param("id") UUID id, @Param("costUsd") BigDecimal costUsd, @Param("price") LlmPriceSnapshot price);

    /** What turns the instance verdict from WITHIN into UNVERIFIABLE: spend {@link #sumCost} cannot see. */
    @Query(
            value = "SELECT EXISTS(SELECT 1 FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "AND e.pricing_state = 'UNPRICED' AND e.funding_source = 'INSTANCE')",
            nativeQuery = true)
    boolean existsUnpricedInstanceFunded(
            @Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * The own-provider mirror of {@link #existsUnpricedInstanceFunded}. Kept separate so each cap is
     * only ever blocked by a blind spot its own owner can clear.
     */
    @Query(
            value = "SELECT EXISTS(SELECT 1 FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "AND e.pricing_state = 'UNPRICED' AND e.funding_source = 'WORKSPACE')",
            nativeQuery = true)
    boolean existsUnpricedWorkspaceFunded(
            @Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            value = "SELECT e.job_type AS jobType, "
                    + "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) "
                    + "AS pricedTotalCostUsd, "
                    + "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'), 0) "
                    + "AS byoTotalCostUsd, "
                    + "COUNT(*) FILTER (WHERE e.pricing_state = 'UNPRICED') AS unpricedEventCount, "
                    + "SUM(e.input_tokens) AS inputTokens, SUM(e.output_tokens) AS outputTokens, "
                    + "SUM(e.cache_read_tokens) AS cacheReadTokens, SUM(e.cache_write_tokens) AS cacheWriteTokens, "
                    + "SUM(e.total_calls) AS totalCalls, COUNT(*) AS events "
                    + "FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "GROUP BY e.job_type ORDER BY pricedTotalCostUsd DESC",
            nativeQuery = true)
    List<JobTypeAggregate> aggregateByJobType(
            @Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            value = "SELECT (e.occurred_at AT TIME ZONE 'UTC')::date AS day, "
                    + "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) "
                    + "AS pricedTotalCostUsd, "
                    + "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'), 0) "
                    + "AS byoTotalCostUsd, "
                    + "COUNT(*) FILTER (WHERE e.pricing_state = 'UNPRICED') AS unpricedEventCount, "
                    + "COUNT(*) AS events "
                    + "FROM llm_usage_event e "
                    + "WHERE e.workspace_id = :workspaceId AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "GROUP BY day ORDER BY day",
            nativeQuery = true)
    List<DailyAggregate> aggregateByDay(
            @Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Instance-admin cross-tenant rollup: every workspace, including those with no spend this window.
     * Unpaged — the row count is the instance's workspace count, and ranking by spend needs the whole
     * month anyway.
     */
    @Query(
            value = "SELECT w.id AS workspaceId, w.slug AS workspaceSlug, w.display_name AS displayName, "
                    + "w.monthly_llm_budget_usd AS monthlyBudgetUsd, "
                    + "w.monthly_byo_llm_budget_usd AS byoMonthlyBudgetUsd, "
                    + "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'INSTANCE'), 0) "
                    + "AS pricedTotalCostUsd, "
                    + "COALESCE(SUM(e.cost_usd) FILTER (WHERE e.pricing_state = 'PRICED' AND e.funding_source = 'WORKSPACE'), 0) "
                    + "AS byoTotalCostUsd, "
                    + "COALESCE(BOOL_OR(e.pricing_state = 'UNPRICED' AND e.funding_source = 'WORKSPACE'), false) "
                    + "AS hasUnpricedByoUsage, "
                    + "COALESCE(bool_or(e.pricing_state = 'UNPRICED' AND e.funding_source = 'INSTANCE'), false) "
                    + "AS hasUnpricedInstanceUsage, "
                    + "COUNT(e.id) AS events "
                    + "FROM workspace w LEFT JOIN llm_usage_event e "
                    + "ON e.workspace_id = w.id AND e.occurred_at >= :from AND e.occurred_at < :to "
                    + "GROUP BY w.id, w.slug, w.display_name, w.monthly_llm_budget_usd, w.monthly_byo_llm_budget_usd "
                    + "ORDER BY pricedTotalCostUsd DESC",
            nativeQuery = true)
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
    @Query(value = """
        SELECT COALESCE(SUM(r.review_cost), 0) AS totalCostUsd, COUNT(*) AS reviews
        FROM (
            SELECT e.source_type, e.source_id, SUM(e.cost_usd) AS review_cost
            FROM llm_usage_event e
            WHERE e.workspace_id = :workspaceId AND e.job_type = :jobType
              AND e.occurred_at >= :from AND e.occurred_at < :to
            GROUP BY e.source_type, e.source_id
            HAVING COUNT(*) FILTER (WHERE e.pricing_state = 'UNPRICED') = 0
        ) r
        """, nativeQuery = true)
    ReviewCostAggregate aggregateCostPerReview(
            @Param("workspaceId") Long workspaceId,
            @Param("jobType") String jobType,
            @Param("from") Instant from,
            @Param("to") Instant to);

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

        @Nullable
        BigDecimal getMonthlyBudgetUsd();

        @Nullable
        BigDecimal getByoMonthlyBudgetUsd();

        BigDecimal getPricedTotalCostUsd();

        BigDecimal getByoTotalCostUsd();

        boolean isHasUnpricedInstanceUsage();

        boolean isHasUnpricedByoUsage();

        long getEvents();
    }
}
