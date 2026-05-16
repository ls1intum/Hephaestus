package de.tum.in.www1.hephaestus.mentor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request body for upserting a vote on a mentor assistant message. */
@Schema(description = "Upsert a vote on a mentor assistant message")
public record ChatMessageVoteRequestDTO(
    @NotNull
    @Schema(description = "true = upvote (helpful), false = downvote", requiredMode = Schema.RequiredMode.REQUIRED)
    Boolean isUpvoted
) {}
