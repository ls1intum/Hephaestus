package de.tum.cit.aet.hephaestus.practices.trace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemEventDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ItemTraceDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ItemEventDTO> events,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReviewRefDTO> reviews,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticeTraceEntryDTO> practices) {
    public ItemTraceDTO {
        events = List.copyOf(events);
        reviews = List.copyOf(reviews);
        practices = List.copyOf(practices);
    }
}
