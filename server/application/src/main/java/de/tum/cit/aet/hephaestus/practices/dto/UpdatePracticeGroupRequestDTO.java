package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UpdatePracticeGroupRequestDTO(
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

        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL)
        boolean shownToDevelopers)
        implements ClosedPracticeInput {}
