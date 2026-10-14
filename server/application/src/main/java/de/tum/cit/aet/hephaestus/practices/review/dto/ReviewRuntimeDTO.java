package de.tum.cit.aet.hephaestus.practices.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ReviewRuntimeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReviewJobDTO> jobs) {
    public ReviewRuntimeDTO {
        jobs = List.copyOf(jobs);
    }
}
