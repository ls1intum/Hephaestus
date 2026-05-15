package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    /**
     * Newest-first slice for the replay window. Callers pass a {@code PageRequest.of(0, N)} and
     * reverse the result in memory to get the trailing chronological tail without loading and
     * trimming the full thread (matches the DB-side LIMIT pattern used by
     * {@code ChatThreadRepository.findRecentThreads}).
     *
     * <p>The {@code id} secondary sort is the tiebreaker for messages persisted in the same
     * {@code persistInFlight} transaction — Postgres {@code TIMESTAMP} is microsecond but
     * Java's {@code Instant.now()} on the JVM clock can produce identical user/assistant
     * timestamps. Without the tiebreaker, replay can ship {@code [assistant, user]} instead of
     * {@code [user, assistant]} and break the LLM's alternating-role contract.
     */
    List<ChatMessage> findByThreadIdOrderByCreatedAtDescIdDesc(UUID threadId, Pageable pageable);

    /**
     * Flip assistant rows still {@code in_flight} past {@code cutoff} to {@code interrupted},
     * AND write a synthetic {@code error: "server restart"} so the chat UI can render a real
     * explanation in the empty assistant bubble. Without the error key the user sees a blank
     * row with no signal that something went wrong. Guards against JVM crashes that leave the
     * per-thread unique partial index permanently tripped. Returns the row count updated for
     * observability.
     */
    @Modifying
    @Transactional
    @WorkspaceAgnostic("Crash-recovery sweep over stuck rows; runs by created_at globally")
    @Query(
        value = """
        UPDATE chat_message
           SET status = 'interrupted',
               metadata = jsonb_set(
                   COALESCE(metadata, '{}'::jsonb),
                   '{error}',
                   '"server restart"'::jsonb,
                   true
               ),
               version = version + 1
         WHERE status = 'in_flight'
           AND created_at < :cutoff
        """,
        nativeQuery = true
    )
    int reapStaleInFlight(@Param("cutoff") Instant cutoff);
}
