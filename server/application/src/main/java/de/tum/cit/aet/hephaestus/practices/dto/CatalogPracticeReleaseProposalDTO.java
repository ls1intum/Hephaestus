package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogPracticeReleaseProposalDTO(
        String practiceSlug,
        PracticeDefinition current,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable
        PracticeDefinition base,

        PracticeDefinition offered,
        String offeredDigest,
        String etag,
        List<PracticeReleaseFieldDTO> fields) {
    public CatalogPracticeReleaseProposalDTO {
        fields = List.copyOf(fields);
    }
}
