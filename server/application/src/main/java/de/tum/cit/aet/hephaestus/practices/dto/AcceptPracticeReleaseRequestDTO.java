package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AcceptPracticeReleaseRequestDTO(
        @JsonProperty(required = true) @NotNull Map<PracticeDefinitionField, @NotNull PracticeReleaseChoice> choices)
        implements ClosedPracticeInput {
    public AcceptPracticeReleaseRequestDTO {
        choices = Map.copyOf(choices);
    }
}
