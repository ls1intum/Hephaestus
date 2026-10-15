package de.tum.cit.aet.hephaestus.practices.scan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ProblemDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanReportProblemDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProblemDTO problem,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID resultId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ProblemBaselineState baselineState,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID previousProblemId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "REPAIRED") @Nullable
        String absentReason) {
    public ScanReportProblemDTO {
        if (absentReason != null && !"REPAIRED".equals(absentReason)) {
            throw new IllegalArgumentException("Invalid absentReason");
        }
    }
}
