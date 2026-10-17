package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MentorConversationQueryRepository extends Repository<ChatThread, UUID> {
    /** Recent chat threads for the user within the workspace, newest first. Caller passes
     *  a {@link Pageable} ({@code PageRequest.of(0, limit)}) so the DB-side LIMIT keeps power
     *  users (hundreds of threads) from hydrating the full list just to {@code subList(0, 10)}. */
    @Query("""
        SELECT t
        FROM ChatThread t
        WHERE t.workspace.id = :workspaceId
          AND t.accountId = :userId
        ORDER BY t.createdAt DESC
        """)
    List<ChatThread> findRecentChatThreads(
            @Param("workspaceId") Long workspaceId, @Param("userId") Long userId, Pageable pageable);

    /**
     * Earliest USER-role message per thread for a batch of thread ids, in one round trip. Native because
     * Postgres {@code DISTINCT ON} is the cheapest path; equivalent JPQL would need a correlated
     * subquery for every thread anyway. Joins {@code chat_thread} on {@code workspace_id} so the
     * query refuses cross-tenant ids the caller might pass — defence in depth even though the
     * upstream id list is already user-scoped.
     *
     * <p>{@code m.parts::text} casts the {@code jsonb} column to text so the projection element is a plain
     * String the caller parses with {@code readTree}. Selecting the raw {@code jsonb} into an untyped
     * {@code Object[]} makes Hibernate apply its JSON Java-type and throw "JSON deserialize failed for String
     * … from Array", silently degrading the prior-conversation context to empty.
     *
     * <p><b>Precondition:</b> {@code threadIds} MUST be non-empty. The native {@code IN (:threadIds)}
     * expands to invalid {@code IN ()} SQL (a Postgres syntax error) for an empty list, so callers must
     * short-circuit beforehand (as {@code WorkspaceContentSource.loadFirstUserMessages} does).
     */
    @Query(value = """
        SELECT DISTINCT ON (m.thread_id) m.thread_id, m.parts::text
          FROM chat_message m
          JOIN chat_thread t ON t.id = m.thread_id
         WHERE m.thread_id IN (:threadIds)
           AND t.workspace_id = :workspaceId
           AND m.role = 'USER'
         ORDER BY m.thread_id, m.created_at ASC
        """, nativeQuery = true)
    List<Object[]> findFirstUserMessagePartsByThreadIds(
            @Param("workspaceId") Long workspaceId, @Param("threadIds") List<UUID> threadIds);
}
