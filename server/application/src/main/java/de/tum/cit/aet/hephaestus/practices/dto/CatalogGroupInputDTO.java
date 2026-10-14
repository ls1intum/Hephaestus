package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogGroupInputDTO(
        @JsonProperty(required = true) @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
        String slug,

        @JsonProperty(required = true) @NotBlank @Size(max = 128)
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @JsonProperty(required = true)
        @Nullable
        @Size(max = 2000)
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @JsonProperty(required = true)
        @Nullable
        @Size(max = 64)
        String icon,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @JsonProperty(required = true)
        @Nullable
        @Size(max = 32)
        String color,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @JsonProperty(required = true)
        @Nullable
        @PositiveOrZero
        Integer position)
        implements ClosedPracticeInput {}
