package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared definition for catalog and workspace areas. */
public record AreaDefinition(
    String name,
    @Nullable String description,
    @Nullable String icon,
    @Nullable String color
) implements CatalogDefinition {
    public AreaDefinition {
        Objects.requireNonNull(name, "name");
        description = blankToNull(description);
        icon = blankToNull(icon);
        color = blankToNull(color);
    }

    public static AreaDefinition from(PracticeArea area) {
        return new AreaDefinition(area.getName(), area.getDescription(), area.getIcon(), area.getColor());
    }

    @Override
    public String digest(String slug) {
        return AreaDefinitionDigest.digest(slug, this);
    }

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
