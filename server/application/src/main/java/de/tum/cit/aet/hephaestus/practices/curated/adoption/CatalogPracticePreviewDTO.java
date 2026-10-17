package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;

public record CatalogPracticePreviewDTO(
        String slug,
        String kind,
        PracticeDefinition definition,
        CatalogAdoptionAvailability availability,
        CatalogAdoptionGroupDTO group,
        FeedbackMode initialFeedbackMode,
        String offeredDigest,
        String etag) {}
