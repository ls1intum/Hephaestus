package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UpdatePracticeRequestDTO(
        @JsonProperty(required = true) PracticeDefinition definition,

        @Schema(pattern = "^[1-9][0-9]*$", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty(required = true)
        String expectedVersionId)
        implements ClosedPracticeInput {
    public UpdatePracticeRequestDTO {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(expectedVersionId);
    }
}
