package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record EvidenceCitationDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceKind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String sourcePath,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String path,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        CitationSide side,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String commit,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer startLine,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer endLine,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String quote,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        CitationProofDTO verification) {}
