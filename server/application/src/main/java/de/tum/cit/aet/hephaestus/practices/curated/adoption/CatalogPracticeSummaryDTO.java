package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.ReviewedBy;
import org.jspecify.annotations.Nullable;

public record CatalogPracticeSummaryDTO(
        String slug,
        String name,
        String kind,
        @Nullable String whyItMatters,
        @Nullable String groupSlug,
        @Nullable String groupName,
        CatalogAdoptionAvailability availability,
        ReviewedBy reviewedBy) {}
