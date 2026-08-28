package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedPracticeDefinitionDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import org.jspecify.annotations.NonNull;

public record CatalogPracticePreviewDTO(
    @NonNull String slug,
    @NonNull CuratedPracticeDefinitionDTO definition,
    @NonNull CatalogAdoptionAvailability availability,
    @NonNull CatalogAdoptionGroupDTO group,
    @NonNull PracticeAutonomy initialAutonomy,
    @NonNull String sourceReviewRuleFingerprint,
    @NonNull String etag
) {}
