package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** A whole curated area definition. The same body creates one and replaces one. */
@Schema(description = "A complete curated practice area definition")
public record CuratedAreaRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    String name,

    @Size(max = 2000, message = "Description must be at most 2000 characters") @Nullable String description,

    @NotNull(message = "Display order is required")
    @Min(value = 0, message = "Display order must not be negative")
    Integer displayOrder,

    @Size(max = 64, message = "Icon must be at most 64 characters") @Nullable String icon,

    @Size(max = 32, message = "Color must be at most 32 characters") @Nullable String color
) {
    public static CuratedAreaRequestDTO of(AreaDefinition definition) {
        return new CuratedAreaRequestDTO(
            definition.name(),
            definition.description(),
            definition.displayOrder(),
            definition.icon(),
            definition.color()
        );
    }

    public AreaDefinition definition() {
        return new AreaDefinition(name, description, displayOrder, icon, color);
    }
}
