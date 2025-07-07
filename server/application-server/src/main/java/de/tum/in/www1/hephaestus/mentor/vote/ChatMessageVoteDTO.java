package de.tum.in.www1.hephaestus.mentor.vote;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for returning vote information.
 */
public record ChatMessageVoteDTO(
    UUID messageId,
    Boolean isUpvoted,
    Instant createdAt,
    Instant updatedAt
) {
    
    /**
     * Create DTO from entity
     */
    public static ChatMessageVoteDTO from(ChatMessageVote vote) {
        return new ChatMessageVoteDTO(
            vote.getMessageId(),
            vote.getIsUpvoted(),
            vote.getCreatedAt(),
            vote.getUpdatedAt()
        );
    }
}
