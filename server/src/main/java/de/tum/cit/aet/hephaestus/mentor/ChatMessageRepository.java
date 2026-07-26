package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-agnostic by intent: the queries that are not workspace-scoped are either the global
 * crash-recovery sweep keyed by {@code created_at} or single-row access by an id the caller already
 * proved it owns; ownership is enforced upstream in {@link ChatThreadService} for every other access
 * path. Class-level {@link WorkspaceAgnostic} satisfies the architecture rule that requires either
 * workspace-scoped query methods or an explicit opt-out marker.
 */
@Repository
@WorkspaceAgnostic("Crash-recovery sweep only; thread-scoped access goes through ChatThreadService")
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    @Query(
        """
        SELECT m
        FROM ChatMessage m
        JOIN m.thread t
        WHERE t.workspace.id = :workspaceId
          AND t.user.id = :userId
          AND t.id = :threadId
          AND m.status = de.tum.cit.aet.hephaestus.mentor.ChatMessage.Status.completed
          AND m.role <> de.tum.cit.aet.hephaestus.mentor.ChatMessage.Role.SYSTEM
          AND (:excludedMessageId IS NULL OR m.id <> :excludedMessageId)
        ORDER BY m.createdAt ASC
        """
    )
    List<ChatMessage> findContextMessages(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId,
        @Param("threadId") UUID threadId,
        @Param("excludedMessageId") UUID excludedMessageId
    );

    @Query(
        "SELECT m FROM ChatMessage m JOIN FETCH m.thread t JOIN FETCH t.workspace " +
            "WHERE m.status = de.tum.cit.aet.hephaestus.mentor.ChatMessage.Status.in_flight AND m.createdAt < :cutoff"
    )
    List<ChatMessage> findStaleInFlightForAccounting(@Param("cutoff") Instant cutoff);

    /**
     * Add ONE served proxy call's tokens to a mentor turn's running totals.
     *
     * <p>{@code status = 'in_flight'} is the fence, and it is the exact counterpart of the
     * {@code retry_count} + {@code status = 'RUNNING'} predicate on {@code agent_job}: a provider call
     * can outlive the turn that issued it, and once the turn has gone terminal these columns are what
     * the accounting paths read. Adding to them afterwards would corrupt the record of a turn that has
     * already been billed, or — worse — charge one turn's tokens to a row someone else now owns. A
     * superseded write matches no row, which the caller reports rather than swallows.
     *
     * <p>Native rather than JPQL because the mapped fields are deliberately {@code updatable = false}
     * (see {@link ChatMessage}); this statement is their single writer. It leaves {@code version}
     * alone on purpose — bumping it would turn every proxied call into a lost-update race against the
     * orchestrator's terminal write.
     *
     * @param id the assistant {@code chat_message} id the call authenticated against
     * @return 1 if the turn is still running, 0 if it has ended (a safe no-op)
     */
    @Modifying
    @Query(
        value = """
        UPDATE chat_message
           SET llm_total_calls = llm_total_calls + 1,
               llm_total_input_tokens = llm_total_input_tokens + :input,
               llm_total_output_tokens = llm_total_output_tokens + :output,
               llm_total_reasoning_tokens = llm_total_reasoning_tokens + :reasoning,
               llm_cache_read_tokens = llm_cache_read_tokens + :cacheRead
         WHERE id = :id
           AND status = 'in_flight'
        """,
        nativeQuery = true
    )
    int accumulateLlmUsage(
        @Param("id") UUID id,
        @Param("input") long input,
        @Param("output") long output,
        @Param("reasoning") long reasoning,
        @Param("cacheRead") long cacheRead
    );

    /**
     * The turn's accumulated proxy usage read straight from the row, so it reflects committed
     * accumulations regardless of how stale the caller's entity is. Callers read this only AFTER
     * writing the turn's terminal status, which locks the row and closes
     * {@link #accumulateLlmUsage}'s fence — so what they read is final.
     */
    @Query(
        "SELECT new de.tum.cit.aet.hephaestus.mentor.MentorTurnLlmUsage(" +
            "m.llmTotalCalls, m.llmTotalInputTokens, m.llmTotalOutputTokens, " +
            "m.llmTotalReasoningTokens, m.llmCacheReadTokens) " +
            "FROM ChatMessage m WHERE m.id = :id"
    )
    Optional<MentorTurnLlmUsage> findLlmUsageById(@Param("id") UUID id);
}
