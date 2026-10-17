package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewLineageDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rootId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReviewLineageNodeDTO> nodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReviewLineageEdgeDTO> edges) {
    public ReviewLineageDTO {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
