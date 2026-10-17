package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record SummaryHandle(String externalRef, @Nullable String url) {
    public SummaryHandle {

        if (externalRef.isBlank()) throw new IllegalArgumentException("Summary reference is blank");
    }
}
