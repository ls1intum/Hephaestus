package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedGroupRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDefinitionDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import org.jspecify.annotations.Nullable;

record CatalogAdoptionPlan(
    String slug,
    PracticeDefinition definition,
    CatalogAdoptionAvailability availability,
    CatalogGroupDisposition groupDisposition,
    @Nullable String groupSlug,
    @Nullable GroupDefinition groupDefinition,
    int groupDisplayOrder,
    PracticeAutonomy initialAutonomy,
    String etag
) {
    static CatalogAdoptionPlan create(
        String slug,
        PracticeDefinition definition,
        CatalogAdoptionAvailability availability,
        CatalogGroupDisposition groupDisposition,
        @Nullable String groupSlug,
        @Nullable GroupDefinition groupDefinition,
        int groupDisplayOrder
    ) {
        PracticeAutonomy initialAutonomy = definition
            .automatedReviewPolicy()
            .automatedReview()
            .canAttemptAutomatedReview()
            ? PracticeAutonomy.HUMAN_APPROVAL
            : PracticeAutonomy.OFF;
        CanonicalDigest digest = new CanonicalDigest()
            .add("catalog-adoption-plan-v1")
            .add(slug)
            .add(definition.digest(slug))
            .add(availability.name())
            .add(groupDisposition.name())
            .addNullable(groupSlug)
            .addNullable(groupDefinition == null || groupSlug == null ? null : groupDefinition.digest(groupSlug))
            .addInt(groupDisplayOrder)
            .add(initialAutonomy.name());
        return new CatalogAdoptionPlan(
            slug,
            definition,
            availability,
            groupDisposition,
            groupSlug,
            groupDefinition,
            groupDisplayOrder,
            initialAutonomy,
            digest.hex()
        );
    }

    CatalogPracticePreviewDTO preview() {
        return new CatalogPracticePreviewDTO(
            slug,
            CuratedPracticeDefinitionDTO.from(slug, definition),
            availability,
            new CatalogAdoptionGroupDTO(
                groupDisposition,
                groupSlug,
                groupDefinition == null ? null : CuratedGroupRequestDTO.of(groupDefinition)
            ),
            initialAutonomy,
            definition.provenanceFingerprint(slug),
            CatalogAdoptionService.formatted(etag)
        );
    }
}
