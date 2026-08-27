package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Current resolution counts for the signed-in recipient. */
@Schema(description = "Feedback resolution counts for the current developer")
public record FeedbackResolutionCountsDTO(long addressed, long disputed, long notApplicable) {}
