package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimBasis;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProblemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Severity severity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID resultId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String key,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String explanation,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EvidenceCitationDTO> citations,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ClaimBasis basis,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String nextStep) {
    public ProblemDTO {
        citations = List.copyOf(citations);
    }
}
