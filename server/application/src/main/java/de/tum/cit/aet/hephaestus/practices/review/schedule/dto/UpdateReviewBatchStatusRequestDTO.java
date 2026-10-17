package de.tum.cit.aet.hephaestus.practices.review.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UpdateReviewBatchStatusRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewBatchStatus status) {}
