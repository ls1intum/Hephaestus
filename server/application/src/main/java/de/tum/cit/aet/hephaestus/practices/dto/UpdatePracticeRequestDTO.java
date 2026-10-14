package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UpdatePracticeRequestDTO(
        @JsonProperty(required = true) @NotNull @Valid PracticeDefinitionInputDTO definition,

        @Schema(pattern = "^[1-9][0-9]*$", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty(required = true)
        @NotNull
        @Pattern(regexp = "[1-9][0-9]*")
        String expectedVersionId)
        implements ClosedPracticeInput {}
