package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogDTO(List<CatalogPracticeDTO> practices, List<CatalogGroupDTO> groups) {
    public CatalogDTO {
        practices = List.copyOf(practices);
        groups = List.copyOf(groups);
    }
}
