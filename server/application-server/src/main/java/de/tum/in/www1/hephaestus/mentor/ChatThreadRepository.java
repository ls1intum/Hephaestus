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

    /** Projection: avoids materialising the full entity to fetch the JSONL blob. Empty when missing or NULL. */
    @Query("SELECT t.sessionJsonl FROM ChatThread t WHERE t.id = :threadId")
    Optional<byte[]> findSessionJsonl(@Param("threadId") UUID threadId);

    /** Projection write: avoids dirty-checking the entity. {@code persistInFlight} guarantees the row exists. */
    @Modifying
    @Transactional
    @Query("UPDATE ChatThread t SET t.sessionJsonl = :bytes WHERE t.id = :threadId")
    int updateSessionJsonl(@Param("threadId") UUID threadId, @Param("bytes") byte[] bytes);

    /**
     * Bulk-delete every thread for a workspace. Cascades to {@code chat_message} →
     * {@code chat_message_part} / {@code chat_message_vote} via existing FKs. Used by
     * {@link de.tum.in.www1.hephaestus.mentor.MentorWorkspacePurgeContributor} on soft purge,
     * which leaves the workspace row in place (so the workspace-level cascade can't fire).
     */
    @Modifying
    @Transactional
    int deleteByWorkspaceId(Long workspaceId);
}
