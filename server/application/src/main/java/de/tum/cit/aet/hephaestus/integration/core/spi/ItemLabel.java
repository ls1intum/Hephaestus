package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record ItemLabel(
        long itemId,
        String title,
        String noun,
        @Nullable Integer number,
        @Nullable String container,
        @Nullable String url,
        @Nullable IntegrationKind integration) {
    public ItemLabel {

        if (itemId <= 0 || title.isBlank() || noun.isBlank()) throw new IllegalArgumentException("Invalid item label");
    }
}
