package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record ItemRegistration(
        long workspaceId,
        ItemKind kind,
        @Nullable Long sourceId,
        @Nullable Long parentId,
        @Nullable String path) {
    public ItemRegistration {

        if (workspaceId <= 0
                || (sourceId != null && sourceId <= 0)
                || (parentId != null && parentId <= 0)
                || ((sourceId == null) == (path == null))
                || (path != null && (path.isBlank() || parentId == null)))
            throw new IllegalArgumentException("Invalid item registration");
    }
}
