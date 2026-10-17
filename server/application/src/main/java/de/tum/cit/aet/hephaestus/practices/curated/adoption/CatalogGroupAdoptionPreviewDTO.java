package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import java.util.List;

public record CatalogGroupAdoptionPreviewDTO(
        String slug,
        GroupDefinition definition,
        CatalogGroupDisposition disposition,
        List<CatalogPracticePreviewDTO> practices,
        List<CatalogGroupPracticeActionDTO> actions,
        String etag) {
    public CatalogGroupAdoptionPreviewDTO {
        practices = List.copyOf(practices);
        actions = List.copyOf(actions);
    }
}
