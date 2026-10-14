package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReorderPracticesRequestDTO(
        @JsonProperty(required = true) @NotNull List<String> slugs) implements ClosedPracticeInput {
    public ReorderPracticesRequestDTO {
        slugs = List.copyOf(slugs);
    }
}
