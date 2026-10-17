package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record BoundModelSlotDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slot,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ModelKind kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FrozenModelDTO model) {}
