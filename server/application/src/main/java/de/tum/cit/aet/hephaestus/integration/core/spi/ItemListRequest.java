package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record ItemListRequest(
        ItemRef container,
        ItemVersion version,
        ItemKind memberKind,
        @Nullable String cursor,
        int limit) {
    public ItemListRequest {

        if (limit <= 0) throw new IllegalArgumentException("Listing limit must be positive");
    }
}
