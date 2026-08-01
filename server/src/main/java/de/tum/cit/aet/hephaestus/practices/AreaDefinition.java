package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The canonical shape of a practice area, shared by the curated catalog and the workspace copies —
 * the area counterpart of {@link PracticeDefinition}.
 */
public record AreaDefinition(
    String name,
    @Nullable String description,
    int displayOrder,
    @Nullable String icon,
    @Nullable String color
) implements CatalogDefinition {
    public AreaDefinition {
        Objects.requireNonNull(name, "name");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        description = blankToNull(description);
        icon = blankToNull(icon);
        color = blankToNull(color);
    }

    public static AreaDefinition from(PracticeArea area) {
        return new AreaDefinition(
            area.getName(),
            area.getDescription(),
            area.getDisplayOrder(),
            area.getIcon(),
            area.getColor()
        );
    }

    @Override
    public String digest(String slug) {
        return AreaDefinitionDigest.digest(slug, this);
    }

    /**
     * Identity of how the area presents. Display order is excluded deliberately: a workspace orders
     * its own areas, and that layout choice must not read as an edit that severs it from the catalog.
     * Areas reach a detection run only as a slug, so this is also what "detects identically" means
     * for one.
     */
    @Override
    public String detectionFingerprint(String slug) {
        return new CanonicalDigest()
            .add(slug)
            .add(name)
            .addNullable(description)
            .addNullable(icon)
            .addNullable(color)
            .hex();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
