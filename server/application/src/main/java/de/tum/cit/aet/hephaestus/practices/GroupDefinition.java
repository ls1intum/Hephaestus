package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record GroupDefinition(
    String name,
    @Nullable String description,
    @Nullable String icon,
    @Nullable String color
) implements CatalogDefinition {
    public GroupDefinition {
        Objects.requireNonNull(name, "name");
        description = blankToNull(description);
        icon = blankToNull(icon);
        color = blankToNull(color);
    }

    public static GroupDefinition from(PracticeGroup group) {
        return new GroupDefinition(group.getName(), group.getDescription(), group.getIcon(), group.getColor());
    }

    @Override
    public String digest(String slug) {
        return GroupDefinitionDigest.digest(slug, this);
    }

    @Override
    public String provenanceFingerprint(String slug) {
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
