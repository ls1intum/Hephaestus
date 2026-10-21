package de.tum.cit.aet.hephaestus.agent.mentor.turn;

import de.tum.cit.aet.hephaestus.agent.adapter.AgentPersonDataCatalog;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The measurement of each Heph turn attempt. {@link MentorAttemptUsageService} only. */
public interface MentorAttemptUsageRepository extends JpaRepository<MentorAttemptUsage, MentorAttemptUsage.Id> {

    /** Taken after the turn row, never before it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM MentorAttemptUsage u WHERE u.workspaceId = :workspaceId AND u.id.turnId = :turnId"
            + " AND u.id.attempt = :attempt")
    Optional<MentorAttemptUsage> lock(
            @Param("workspaceId") long workspaceId, @Param("turnId") UUID turnId, @Param("attempt") int attempt);

    /** Every attempt of one turn, locked; taken after the turn row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM MentorAttemptUsage u WHERE u.workspaceId = :workspaceId AND u.id.turnId = :turnId"
            + " ORDER BY u.id.attempt")
    List<MentorAttemptUsage> lockAttempts(@Param("workspaceId") long workspaceId, @Param("turnId") UUID turnId);

    /**
     * Creates the attempt's measurement, empty, with the model and price its admission froze. Creating it counts no
     * call. The caller holds the turn row on this attempt, which the foreign key needs to exist.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO mentor_turn_attempt_usage
            (turn_id, attempt, workspace_id, model, funding_source, pricing_state, applied_price_id,
             applied_workspace_model_id, applied_per_1m_input_usd, applied_per_1m_output_usd,
             applied_per_1m_cache_read_usd, applied_per_1m_cache_write_usd, calls, input_tokens, output_tokens,
             cache_read_tokens, cache_write_tokens, reasoning_tokens)
        VALUES (:turnId, :attempt, :workspaceId, :model, :#{#price.fundingSource().name()},
            :#{#price.pricingState().name()}, :#{#price.appliedPriceId()}, :#{#price.appliedWorkspaceModelId()},
            :#{#price.per1mInputUsd()}, :#{#price.per1mOutputUsd()}, :#{#price.per1mCacheReadUsd()},
            :#{#price.per1mCacheWriteUsd()}, 0, 0, 0, 0, 0, 0)
        ON CONFLICT (turn_id, attempt) DO NOTHING
        """, nativeQuery = true)
    int register(
            @Param("turnId") UUID turnId,
            @Param("attempt") int attempt,
            @Param("workspaceId") long workspaceId,
            @Param("model") String model,
            @Param("price") LlmPriceSnapshot price);

    /**
     * Dates the attempt with its end, unless an earlier end dated it. The caller ends the attempt in this transaction
     * and holds the turn row.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value = "UPDATE mentor_turn_attempt_usage SET occurred_at = :endedAt WHERE workspace_id = :workspaceId"
                    + " AND turn_id = :turnId AND attempt = :attempt AND occurred_at IS NULL",
            nativeQuery = true)
    int dateOnce(
            @Param("workspaceId") long workspaceId,
            @Param("turnId") UUID turnId,
            @Param("attempt") int attempt,
            @Param("endedAt") Instant endedAt);

    /** Removes every attempt measurement of one turn; the caller holds the turn row. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "DELETE FROM mentor_turn_attempt_usage WHERE workspace_id = :workspaceId AND turn_id = :turnId",
            nativeQuery = true)
    int deleteAttempts(@Param("workspaceId") long workspaceId, @Param("turnId") UUID turnId);

    /**
     * Detaches every ledger row of one turn, the projections of its measurements, with the ledger store's own
     * redaction. The caller holds the turn row.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value = "UPDATE llm_usage_event SET " + AgentPersonDataCatalog.LEDGER_REDACTION
                    + " WHERE workspace_id = :workspaceId AND source_type = 'MENTOR_TURN' AND source_id = :turnId",
            nativeQuery = true)
    int detachLedger(@Param("workspaceId") long workspaceId, @Param("turnId") UUID turnId);
}
