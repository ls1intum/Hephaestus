package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;

public record ReviewCapabilities(
        List<ReviewLimitation> reviewLimitations,
        List<ReviewStateDimension> reviewOnStateDimensions,
        Set<String> appliesWhenSupportedAspects,
        Set<String> appliesWhenEvidenceCollections,
        Set<ItemKind> scanItemKinds) {
    public ReviewCapabilities {
        reviewLimitations = List.copyOf(reviewLimitations);
        reviewOnStateDimensions = List.copyOf(reviewOnStateDimensions);
        appliesWhenSupportedAspects = Set.copyOf(appliesWhenSupportedAspects);
        appliesWhenEvidenceCollections = Set.copyOf(appliesWhenEvidenceCollections);
        scanItemKinds = Set.copyOf(scanItemKinds);
    }
}
