package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeDefinitionOptionsDTO(List<PracticeItemTypeOptionDTO> itemTypes, String sourceContract) {
    public PracticeDefinitionOptionsDTO {
        itemTypes = List.copyOf(itemTypes);
    }
}
