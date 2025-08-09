package de.tum.in.www1.hephaestus.mentor.vote;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.ChatMessageRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing chat message votes.
 */
@Service
@Transactional
public class ChatMessageVoteService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageVoteService.class);

    private final ChatMessageVoteRepository voteRepository;
    private final ChatMessageRepository messageRepository;

    public ChatMessageVoteService(ChatMessageVoteRepository voteRepository, ChatMessageRepository messageRepository) {
        this.voteRepository = voteRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Vote on a message (upsert behavior).
     * Updates existing vote or creates new one.
     */
    public ChatMessageVoteDTO voteMessage(UUID messageId, Boolean isUpvoted) {
        logger.debug("Processing vote for message: {} with vote: {}", messageId, isUpvoted);

        // Validate message exists
        var messageOpt = messageRepository.findById(messageId);
        if (messageOpt.isEmpty()) {
            throw new IllegalArgumentException("Message not found: " + messageId);
        }
        // Update existing first
        int updated = voteRepository.updateVote(messageId, Boolean.TRUE.equals(isUpvoted));
        if (updated == 0) {
            // Insert if absent, deriving thread_id from chat_message
            voteRepository.insertVoteIfAbsent(messageId, Boolean.TRUE.equals(isUpvoted));
        }
        // Load and return
        ChatMessageVote persisted = voteRepository
            .findById(messageId)
            .orElse(new ChatMessageVote(messageId, isUpvoted));
        persisted.setIsUpvoted(isUpvoted);
        logger.debug("Saved vote for message: {} as {}", messageId, isUpvoted ? "upvote" : "downvote");
        return ChatMessageVoteDTO.from(persisted);
    }

    /**
     * Get vote for a specific message.
     */
    public Optional<ChatMessageVoteDTO> getVote(UUID messageId) {
        return voteRepository.findById(messageId).map(ChatMessageVoteDTO::from);
    }

    /**
     * Get votes for multiple messages (for bulk loading conversation votes).
     */
    public List<ChatMessageVoteDTO> getVotes(List<UUID> messageIds) {
        return voteRepository.findByMessageIdIn(messageIds).stream().map(ChatMessageVoteDTO::from).toList();
    }

    /**
     * Remove vote for a message.
     */
    public void removeVote(UUID messageId) {
        if (voteRepository.existsById(messageId)) {
            voteRepository.deleteById(messageId);
            logger.debug("Removed vote for message: {}", messageId);
        }
    }

    /**
     * Get all votes for messages in a thread.
     * Verifies user ownership of the thread.
     */
    public List<ChatMessageVoteDTO> getVotesForThread(UUID threadId, User user) {
        logger.debug("Getting votes for thread: {} by user: {}", threadId, user.getLogin());

        // Verify thread exists and user owns it
        // This will be implemented when we have proper thread ownership checking
        // For now, we'll get all votes for messages in the thread

        return voteRepository.findByThreadId(threadId).stream().map(ChatMessageVoteDTO::from).toList();
    }
}
