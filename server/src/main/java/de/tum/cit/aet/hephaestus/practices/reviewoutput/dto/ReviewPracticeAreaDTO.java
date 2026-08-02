package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ReviewPracticeAreaDTO(
    @NonNull String slug,
    @NonNull String name,
    @Schema(description = "Lucide icon name") String icon,
    @Schema(description = "Palette color key") String color
) {
    public static ReviewPracticeAreaDTO from(PracticeArea area) {
        return new ReviewPracticeAreaDTO(area.getSlug(), area.getName(), area.getIcon(), area.getColor());
    }

    public static @Nullable ReviewPracticeAreaDTO from(
        @Nullable String slug,
        @Nullable String name,
        @Nullable String icon,
        @Nullable String color
    ) {
        return slug == null ? null : new ReviewPracticeAreaDTO(slug, name, icon, color);
    }
}
