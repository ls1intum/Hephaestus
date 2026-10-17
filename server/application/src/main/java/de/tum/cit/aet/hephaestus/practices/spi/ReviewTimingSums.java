package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;

public record ReviewTimingSums(
        double asked,
        double queue,
        double preparation,
        double model,
        double runUnsplit,
        @Nullable Double posted) {}
