package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OpenQuestionDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String openQuestion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String wouldSettleIt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        EvidenceCitationDTO citation) {}
