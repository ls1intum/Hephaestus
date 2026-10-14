package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogEntryState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogOriginDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String catalogSlug,

        CatalogEntryState state,
        List<PracticeDefinitionField> localEdits,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        String offeredDigest) {
    public CatalogOriginDTO {
        localEdits = List.copyOf(localEdits);
    }
}
