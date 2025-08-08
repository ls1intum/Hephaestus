package de.tum.in.www1.hephaestus.mentor.vote;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
