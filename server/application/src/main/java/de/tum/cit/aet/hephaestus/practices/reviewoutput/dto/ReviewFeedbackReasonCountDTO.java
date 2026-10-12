package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "How much feedback was withheld for one reason")
public record ReviewFeedbackReasonCountDTO(
        @Nullable
        @Schema(description = "Why Hephaestus withheld it; absent for withheld feedback whose reason is not recorded")
        FeedbackSuppressionReason reason,

        @NonNull @Schema(description = "The feedback withheld for this reason")
        Long count) {}
