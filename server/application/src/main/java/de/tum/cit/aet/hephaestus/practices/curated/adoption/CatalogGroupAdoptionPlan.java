package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedGroupRequestDTO;
import java.util.List;

record CatalogGroupAdoptionPlan(
    String slug,
    GroupDefinition definition,
    CatalogGroupDisposition disposition,
    int displayOrder,
    List<CatalogAdoptionPlan> practices,
    List<CatalogGroupPracticeActionDTO> actions,
    String etag
) {
    static CatalogGroupAdoptionPlan create(
        String slug,
        GroupDefinition definition,
        CatalogGroupDisposition disposition,
        int displayOrder,
        List<CatalogAdoptionPlan> practices,
        List<CatalogGroupPracticeActionDTO> actions
    ) {
        CanonicalDigest digest = new CanonicalDigest()
            .add("catalog-group-adoption-plan-v1")
            .add(slug)
            .add(definition.digest(slug))
            .add(disposition.name())
            .addInt(displayOrder);
        practices.forEach(practice -> digest.add(practice.etag()));
        actions.forEach(action -> digest.add(action.slug()).add(action.action().name()));
        return new CatalogGroupAdoptionPlan(
            slug,
            definition,
            disposition,
            displayOrder,
            List.copyOf(practices),
            List.copyOf(actions),
            digest.hex()
        );
    }

    CatalogGroupAdoptionPreviewDTO preview() {
        return new CatalogGroupAdoptionPreviewDTO(
            slug,
            CuratedGroupRequestDTO.of(definition),
            disposition,
            practices.stream().map(CatalogAdoptionPlan::preview).toList(),
            actions,
            CatalogAdoptionService.formatted(etag)
        );
    }
}
