package de.tum.cit.aet.hephaestus.practices;

import org.jspecify.annotations.Nullable;

public record GroupAttributes(
        String name,
        @Nullable String description,
        @Nullable Integer position,
        @Nullable String icon,
        @Nullable String color) {}
