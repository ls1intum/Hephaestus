package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import java.util.List;

record CatalogAreaAdoptionPlan(
    String slug,
    AreaDefinition definition,
    CatalogAreaDisposition disposition,
    int displayOrder,
    List<CatalogAdoptionPlan> practices,
    List<CatalogAreaPracticeActionDTO> actions,
    String etag
) {
    static CatalogAreaAdoptionPlan create(
        String slug,
        AreaDefinition definition,
        CatalogAreaDisposition disposition,
        int displayOrder,
        List<CatalogAdoptionPlan> practices,
        List<CatalogAreaPracticeActionDTO> actions
    ) {
        CanonicalDigest digest = new CanonicalDigest()
            .add("catalog-area-adoption-plan-v1")
            .add(slug)
            .add(definition.digest(slug))
            .add(disposition.name())
            .addInt(displayOrder);
        practices.forEach(practice -> digest.add(practice.etag()));
        actions.forEach(action -> digest.add(action.slug()).add(action.action().name()));
        return new CatalogAreaAdoptionPlan(
            slug,
            definition,
            disposition,
            displayOrder,
            List.copyOf(practices),
            List.copyOf(actions),
            digest.hex()
        );
    }

    CatalogAreaAdoptionPreviewDTO preview() {
        return new CatalogAreaAdoptionPreviewDTO(
            slug,
            CuratedAreaRequestDTO.of(definition),
            disposition,
            practices.stream().map(CatalogAdoptionPlan::preview).toList(),
            actions,
            CatalogAdoptionService.formatted(etag)
        );
    }
}
