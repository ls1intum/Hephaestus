package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GeneratedPathReviewDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> patterns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> excludedPaths,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean withheld,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reason) {
    public GeneratedPathReviewDTO {
        patterns = List.copyOf(patterns);
        excludedPaths = List.copyOf(excludedPaths);
    }
}
