package de.tum.in.www1.hephaestus.mentor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, UUID> {
    /**
     * List threads owned by the given user inside the given workspace, newest first.
     * Workspace + owner scoping is enforced at the query layer so no controller can
     * accidentally leak threads across tenants.
     */
    List<ChatThread> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(Long workspaceId, Long userId);

    /**
     * Resolve a thread within a workspace; returns empty when the thread either does not
     * exist or belongs to a different workspace.
     */
    Optional<ChatThread> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    /**
     * Bypass-the-entity read of {@code session_jsonl}. Used by the cold-container restore
     * path in {@code MentorChatService} so we don't materialise the full entity (with its
     * lazy collections) just to retrieve one column. Returns {@code Optional.empty()} when
     * either the thread doesn't exist OR the column is NULL (fresh thread, no turns yet).
     */
    @Query("SELECT t.sessionJsonl FROM ChatThread t WHERE t.id = :threadId")
    Optional<byte[]> findSessionJsonl(@Param("threadId") UUID threadId);

    /**
     * Bypass-the-entity write of {@code session_jsonl}. Used by
     * {@code MentorTurnPersistence.finalise} so the blob update lives in the same
     * REQUIRES_NEW transaction as the assistant row's status flip without round-tripping
     * the whole {@link ChatThread} entity (which would also dirty-check unrelated columns).
     *
     * <p>Returns the affected row count so callers can detect a missing thread (the result
     * should always be 1 in production paths since the thread is guaranteed to exist by
     * {@code persistInFlight} earlier in the same logical turn).
     */
    @Modifying
    @Transactional
    @Query("UPDATE ChatThread t SET t.sessionJsonl = :bytes WHERE t.id = :threadId")
    int updateSessionJsonl(@Param("threadId") UUID threadId, @Param("bytes") byte[] bytes);

    /**
     * Count threads for a workspace — used by the workspace purge path to size the audit log
     * before issuing the bulk delete.
     */
    long countByWorkspaceId(Long workspaceId);

    /**
     * Bulk-delete every thread for a workspace. Cascades to {@code chat_message} →
     * {@code chat_message_part} / {@code chat_message_vote} via the FK ON DELETE CASCADE
     * defined in {@code 1778756946278_changelog.xml}. Required by {@code purgeWorkspace}
     * because soft-purging (status=PURGED) the workspace row doesn't trigger the DDL cascade,
     * leaving chat conversations indefinitely accessible to the workspace's prior users —
     * a GDPR Art. 17 erasure gap.
     */
    @Modifying
    @Transactional
    int deleteByWorkspaceId(Long workspaceId);
}
