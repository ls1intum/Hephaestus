package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CreatePracticeRequestDTO(
        @JsonProperty(required = true) @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
        String slug,

        @JsonProperty(required = true) @NotNull @Pattern(regexp = "[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*")
        String kind,

        @JsonProperty(required = true) @NotNull @Valid PracticeDefinition definition,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        String groupSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @JsonProperty(required = true)
        @Nullable
        @PositiveOrZero
        Integer position)
        implements ClosedPracticeInput {}
