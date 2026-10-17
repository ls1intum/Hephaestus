package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record ItemVersion(String value, @Nullable String baseVersion) {
    public ItemVersion {

        if (value.isBlank()
                || value.length() > 1024
                || (baseVersion != null && (baseVersion.isBlank() || baseVersion.length() > 1024)))
            throw new IllegalArgumentException("Invalid item version");
    }
}
