package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record StateDimensionDTO(String name, String label, List<Value> values) {
    public StateDimensionDTO {
        values = List.copyOf(values);
    }

    public record Value(String value, String label) {}
}
