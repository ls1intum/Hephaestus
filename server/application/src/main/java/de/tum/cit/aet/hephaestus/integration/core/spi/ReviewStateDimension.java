package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;

public record ReviewStateDimension(
        String key, String displayName, List<ReviewStateValue> values, Set<String> recommendedValues) {
    public ReviewStateDimension {
        values = List.copyOf(values);
        recommendedValues = Set.copyOf(recommendedValues);
    }
}
