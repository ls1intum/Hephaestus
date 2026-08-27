package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A complete curated practice group definition")
public record CuratedGroupRequestDTO(
        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
        @NonNull
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters") @Nullable
        String description,

        @Size(max = 64, message = "Icon must be at most 64 characters") @Nullable
        String icon,

        @Size(max = 32, message = "Color must be at most 32 characters") @Nullable
        String color) {
    public static CuratedGroupRequestDTO of(GroupDefinition definition) {
        return new CuratedGroupRequestDTO(
                definition.name(), definition.description(), definition.icon(), definition.color());
    }

    public GroupDefinition definition() {
        return new GroupDefinition(name, description, icon, color);
    }
}
