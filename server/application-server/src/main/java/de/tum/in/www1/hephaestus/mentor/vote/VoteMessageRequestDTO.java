package de.tum.in.www1.hephaestus.mentor.vote;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for voting on a message.
 */
public record VoteMessageRequestDTO(
    @NotNull(message = "Vote is required")
    Boolean isUpvoted
) {
    // true = upvote (helpful), false = downvote (not helpful)
}
