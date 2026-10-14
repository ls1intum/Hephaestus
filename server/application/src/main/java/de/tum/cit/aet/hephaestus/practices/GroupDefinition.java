package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GroupDefinition(
        String name,
        @Nullable String description,
        @Nullable String icon,
        @Nullable String color) implements ClosedPracticeInput {
    public GroupDefinition {
        Objects.requireNonNull(name);
        if (name.isBlank()
                || name.length() > 128
                || (description != null && description.length() > 2000)
                || (icon != null && icon.length() > 64)
                || (color != null && color.length() > 32)) {
            throw new IllegalArgumentException("Use a group name and metadata within the supported lengths.");
        }
    }
}
