package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ItemFactDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String value,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String url) {}
