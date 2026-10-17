package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackDisagreementDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID feedbackId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackDestination destination,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String comment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant respondedAt) {}
