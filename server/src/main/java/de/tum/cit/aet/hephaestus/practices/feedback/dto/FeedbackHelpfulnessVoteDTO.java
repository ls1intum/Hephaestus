package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackHelpfulnessVote;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "Current usefulness rating for a delivered feedback unit")
public record FeedbackHelpfulnessVoteDTO(@NonNull UUID feedbackId, boolean helpful, @NonNull Instant updatedAt) {
    public static FeedbackHelpfulnessVoteDTO from(FeedbackHelpfulnessVote vote) {
        return new FeedbackHelpfulnessVoteDTO(vote.getFeedbackId(), vote.getHelpful(), vote.getUpdatedAt());
    }
}
