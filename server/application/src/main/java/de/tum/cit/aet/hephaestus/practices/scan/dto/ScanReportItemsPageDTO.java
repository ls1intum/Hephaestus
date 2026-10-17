package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanReportItemsPageDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ScanReportItemDTO> content,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String nextCursor,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasNext) {
    public ScanReportItemsPageDTO {
        content = List.copyOf(content);
    }
}
