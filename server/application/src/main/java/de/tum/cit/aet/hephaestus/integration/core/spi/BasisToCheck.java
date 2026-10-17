package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record BasisToCheck(
        int problemIndex,
        String type,
        int headCitation,
        @Nullable Integer baseCitation) {}
