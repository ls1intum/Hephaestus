package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record ItemRef(
        long workspaceId,
        long itemId,
        ItemKind kind,
        @Nullable Long sourceId,
        @Nullable Long parentId,
        @Nullable String path) {
    public ItemRef {

        if (workspaceId <= 0
                || itemId <= 0
                || (sourceId != null && sourceId <= 0)
                || (parentId != null && (parentId <= 0 || parentId == itemId))
                || ((sourceId == null) == (path == null))
                || (path != null && (path.isBlank() || parentId == null)))
            throw new IllegalArgumentException("Invalid item reference");
    }
}
