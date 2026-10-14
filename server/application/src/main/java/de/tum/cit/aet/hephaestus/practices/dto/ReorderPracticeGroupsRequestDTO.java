package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReorderPracticeGroupsRequestDTO(
        @JsonProperty(required = true) List<String> slugs) implements ClosedPracticeInput {
    public ReorderPracticeGroupsRequestDTO {
        slugs = List.copyOf(slugs);
    }
}
