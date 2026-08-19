package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewValidation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CatalogPracticeSummaryDTO(
    @NonNull String slug,
    @NonNull String name,
    @NonNull ArtifactKind artifactKind,
    @Nullable String areaSlug,
    @Nullable String areaName,
    @NonNull CatalogAdoptionAvailability availability,
    @NonNull PracticeAutomatedReviewValidation automatedReviewValidation
) {}
