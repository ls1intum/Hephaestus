package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;

public record ReviewTimingStage(
        long count,
        @Nullable Double medianSeconds,
        @Nullable Double p90Seconds) {}
