package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewedPracticeDTO(
        String slug,
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        GroupRefDTO group,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String whyItMatters,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String whatGoodLooksLike) {}
