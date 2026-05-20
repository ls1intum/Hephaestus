package de.tum.cit.aet.hephaestus.mentor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "User vote on a mentor assistant message.")
public record ChatMessageVoteDTO(UUID messageId, boolean isUpvoted, Instant updatedAt) {
    public static ChatMessageVoteDTO from(ChatMessageVote vote) {
        return new ChatMessageVoteDTO(vote.getMessageId(), vote.getIsUpvoted(), vote.getUpdatedAt());
    }
}
