package de.tum.cit.aet.hephaestus.practices.trace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OwnItemTraceDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewRefDTO review,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OwnItemEventDTO> events,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResultListDTO> results) {
    public OwnItemTraceDTO {
        events = List.copyOf(events);
        results = List.copyOf(results);
    }
}
