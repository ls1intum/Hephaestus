package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeDTO(
        @Schema(pattern = "^[1-9][0-9]*$", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        String slug,
        String kind,
        PracticeVersionDTO currentVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String groupSlug,

        int position,
        FeedbackModeAssignmentDTO feedbackMode,
        CatalogOriginDTO catalogOrigin,
        Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        Instant updatedAt) {}
