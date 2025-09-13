package de.tum.in.www1.hephaestus.mentor.vote;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ChatMessageVote operations.
 */
@Repository
public interface ChatMessageVoteRepository extends JpaRepository<ChatMessageVote, UUID> {
    /**
     * Find all votes for messages in a specific chat thread.
     * Used for bulk fetching votes for a conversation.
     */
    List<ChatMessageVote> findByMessageIdIn(List<UUID> messageIds);

    /**
     * Find all votes for messages in a specific thread.
     */
    @Query(
        """
        SELECT v FROM ChatMessageVote v
        JOIN ChatMessage m ON v.messageId = m.id
        WHERE m.thread.id = :threadId
        """
    )
    List<ChatMessageVote> findByThreadId(@Param("threadId") UUID threadId);

    /**
     * Update existing vote. Returns number of rows affected.
     */
    @Modifying
    @Query(
        value = "UPDATE chat_message_vote SET is_upvoted = :isUpvoted, updated_at = NOW() WHERE message_id = :messageId",
        nativeQuery = true
    )
    int updateVote(@Param("messageId") UUID messageId, @Param("isUpvoted") boolean isUpvoted);

    /**
     * Insert vote if absent.
     * Returns number of rows inserted (0 if already exists or message missing).
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO chat_message_vote (message_id, is_upvoted, created_at, updated_at)
        SELECT :messageId, :isUpvoted, NOW(), NOW()
        FROM chat_message m
        WHERE m.id = :messageId
          AND NOT EXISTS (SELECT 1 FROM chat_message_vote v WHERE v.message_id = :messageId)
        """,
        nativeQuery = true
    )
    int insertVoteIfAbsent(@Param("messageId") UUID messageId, @Param("isUpvoted") boolean isUpvoted);
}
