package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace-agnostic by intent: the only entry point is a global crash-recovery sweep keyed by
 * {@code created_at}; ownership is enforced upstream in {@link ChatThreadService} for every
 * non-sweep access path. Class-level {@link WorkspaceAgnostic} satisfies the architecture rule
 * that requires either workspace-scoped query methods or an explicit opt-out marker.
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
