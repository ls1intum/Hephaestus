package de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeWorkspaceOverviewDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int developers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticeWorkspaceTileDTO> practices) {
    public PracticeWorkspaceOverviewDTO {
        practices = List.copyOf(practices);
    }
}
