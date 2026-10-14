package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AcceptPracticeReleaseRequestDTO(
        @JsonProperty(required = true) List<Choice> choices) implements ClosedPracticeInput {
    public AcceptPracticeReleaseRequestDTO {
        choices = List.copyOf(choices);
        if (choices.stream().map(Choice::field).distinct().count() != choices.size())
            throw new IllegalArgumentException("Choose each release field once.");
    }

    public record Choice(
            @JsonProperty(required = true) PracticeDefinitionField field,
            @JsonProperty(required = true) PracticeReleaseChoice source)
            implements ClosedPracticeInput {
        public Choice {
            Objects.requireNonNull(field);
            Objects.requireNonNull(source);
        }
    }

    public Map<PracticeDefinitionField, PracticeReleaseChoice> asMap() {
        return choices.stream().collect(Collectors.toUnmodifiableMap(Choice::field, Choice::source));
    }
}
