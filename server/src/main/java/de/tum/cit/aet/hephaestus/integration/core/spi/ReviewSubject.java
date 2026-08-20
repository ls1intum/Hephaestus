package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record ReviewSubject(@Nullable Long actorId, boolean human) {
    public static final ReviewSubject MISSING = new ReviewSubject(null, false);
}
