package de.tum.cit.aet.hephaestus.practices;

import org.jspecify.annotations.Nullable;

/**
 * The mutable display attributes of a {@link de.tum.cit.aet.hephaestus.practices.model.PracticeArea},
 * bundled as a parameter object so {@code createArea}/{@code updateArea} take the area's identity
 * ({@code ctx}, {@code slug}) plus this one value rather than a long positional argument list.
 *
 * <p>All fields are nullable: on update a {@code null} leaves the existing value untouched; on create a
 * {@code null} {@code displayOrder} defaults to {@code 0}.
 */
public record AreaAttributes(
    @Nullable String name,
    @Nullable String description,
    @Nullable Integer displayOrder,
    @Nullable String icon,
    @Nullable String color
) {}
