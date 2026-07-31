package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeArea;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record CuratedPracticeAreaDTO(
    @NonNull String slug,
    @NonNull String name,
    @Nullable String description,
    @NonNull Integer displayOrder,
    @Nullable String icon,
    @Nullable String color
) {
    public static CuratedPracticeAreaDTO from(CuratedPracticeArea area) {
        return new CuratedPracticeAreaDTO(
            area.getSlug(),
            area.getName(),
            area.getDescription(),
            area.getDisplayOrder(),
            area.getIcon(),
            area.getColor()
        );
    }
}
