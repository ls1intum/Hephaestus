package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDefinitionDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import org.jspecify.annotations.Nullable;

record CatalogAdoptionPlan(
    String slug,
    PracticeDefinition definition,
    CatalogAdoptionAvailability availability,
    CatalogAreaDisposition areaDisposition,
    @Nullable String areaSlug,
    @Nullable AreaDefinition areaDefinition,
    int areaDisplayOrder,
    String etag
) {
    static CatalogAdoptionPlan create(
        String slug,
        PracticeDefinition definition,
        CatalogAdoptionAvailability availability,
        CatalogAreaDisposition areaDisposition,
        @Nullable String areaSlug,
        @Nullable AreaDefinition areaDefinition,
        int areaDisplayOrder
    ) {
        CanonicalDigest digest = new CanonicalDigest()
            .add("catalog-adoption-plan-v1")
            .add(slug)
            .add(definition.digest(slug))
            .add(availability.name())
            .add(areaDisposition.name())
            .addNullable(areaSlug)
            .addNullable(areaDefinition == null || areaSlug == null ? null : areaDefinition.digest(areaSlug))
            .addInt(areaDisplayOrder)
            .add(PracticeAutonomy.HUMAN_APPROVAL.name());
        return new CatalogAdoptionPlan(
            slug,
            definition,
            availability,
            areaDisposition,
            areaSlug,
            areaDefinition,
            areaDisplayOrder,
            digest.hex()
        );
    }

    CatalogPracticePreviewDTO preview() {
        return new CatalogPracticePreviewDTO(
            slug,
            CuratedPracticeDefinitionDTO.from(slug, definition),
            availability,
            new CatalogAdoptionAreaDTO(
                areaDisposition,
                areaSlug,
                areaDefinition == null ? null : CuratedAreaRequestDTO.of(areaDefinition)
            ),
            PracticeAutonomy.HUMAN_APPROVAL,
            definition.provenanceFingerprint(slug),
            CatalogAdoptionService.formatted(etag)
        );
    }
}
