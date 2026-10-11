package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Feedback at one location, one count per delivery state")
public record ReviewFeedbackLocationCountsDTO(
        @NonNull @Schema(description = "Where the feedback appears, by the provider that shows it")
        FeedbackLocation location,

        @NonNull @Schema(description = "The feedback at this location, by delivery state")
        ReviewFeedbackCountsDTO feedback) {}
