package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.practices.spi.CitationProof;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProjectedCitationDTO(
        String sourceKind,
        @Nullable String sourcePath,
        @Nullable String path,
        @Nullable CitationSide side,
        @Nullable String revision,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        @Nullable String quote,
        @Nullable CitationProof verification,
        int index,
        boolean anchorable) {}
