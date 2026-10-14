package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ResultEvidenceDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EvidenceCitationDTO> citations,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        SearchWarrant search,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        InapplicabilityWarrant inapplicability,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OpenQuestionDTO> openQuestions) {
    public ResultEvidenceDTO {
        citations = List.copyOf(citations);
        openQuestions = List.copyOf(openQuestions);
    }
}
