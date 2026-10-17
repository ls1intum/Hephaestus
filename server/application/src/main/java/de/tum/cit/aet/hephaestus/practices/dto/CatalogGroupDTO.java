package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogEntryState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogGroupDTO(
        String slug,
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String icon,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String color,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        GroupDefinition shipped,

        int position,
        boolean excluded,
        CatalogEntryState state,
        CatalogChangeKind changeKind,
        boolean offered,
        String etag) {}
