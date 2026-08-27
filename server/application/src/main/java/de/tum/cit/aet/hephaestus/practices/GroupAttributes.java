package de.tum.cit.aet.hephaestus.practices;

import org.jspecify.annotations.Nullable;

public record GroupAttributes(
    @Nullable String name,
    @Nullable String description,
    @Nullable Integer displayOrder,
    @Nullable String icon,
    @Nullable String color
) {}
