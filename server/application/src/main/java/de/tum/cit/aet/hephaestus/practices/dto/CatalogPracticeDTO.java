package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogEntryState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogPracticeDTO(
        String slug,
        String kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String groupSlug,

        PracticeDefinition definition,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        PracticeDefinition shipped,

        int position,
        boolean excluded,
        CatalogEntryState state,
        CatalogChangeKind changeKind,
        boolean offered,
        String etag) {}
