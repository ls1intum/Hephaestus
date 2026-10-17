package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewMethodDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int epoch,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelDigest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BoundModelSlotDTO> models) {
    public ReviewMethodDTO {
        models = List.copyOf(models);
    }
}
