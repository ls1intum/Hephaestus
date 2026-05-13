package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert/delete operations for {@link ChatMessageVote}. Ownership is enforced indirectly:
 * the vote can only target a message inside a thread the caller already owns, which is
 * verified by {@link ChatThreadService#getOwnedThread(Long, UUID)} before dispatch.
 *
 * <p>The vote table uses {@code message_id} as the primary key, so each user/message pair
 * collapses to a single row — repeated POSTs simply flip {@code is_upvoted}.
 */
@Service
@RequiredArgsConstructor
public class ChatMessageVoteService {

    private final ChatMessageVoteRepository chatMessageVoteRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Verifies the message exists inside the given thread, then upserts the vote.
     *
     * @throws EntityNotFoundException when the message does not exist or does not belong
     *                                 to the supplied thread.
     */
    @Transactional
    public ChatMessageVote upsert(UUID threadId, UUID messageId, boolean isUpvoted) {
        ChatMessage message = chatMessageRepository
            .findById(messageId)
            .filter(m -> m.getThread() != null && m.getThread().getId().equals(threadId))
            .orElseThrow(() -> new EntityNotFoundException("ChatMessage", messageId.toString()));
        ChatMessageVote vote = chatMessageVoteRepository
            .findById(message.getId())
            .orElseGet(() -> new ChatMessageVote(message.getId(), isUpvoted));
        vote.setIsUpvoted(isUpvoted);
        return chatMessageVoteRepository.save(vote);
    }

    /** No-op if the vote does not exist (idempotent DELETE semantics). */
    @Transactional
    public void delete(UUID threadId, UUID messageId) {
        chatMessageRepository
            .findById(messageId)
            .filter(m -> m.getThread() != null && m.getThread().getId().equals(threadId))
            .orElseThrow(() -> new EntityNotFoundException("ChatMessage", messageId.toString()));
        chatMessageVoteRepository.deleteById(messageId);
    }
}
