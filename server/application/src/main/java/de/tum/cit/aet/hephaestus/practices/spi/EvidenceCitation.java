package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceCitation(
        SourceKind sourceKind,
        String sourcePath,
        String path,
        @Nullable CitationSide side,
        @Nullable String revision,
        int startLine,
        int endLine,
        String quote,
        @Nullable CitationProof verification) {}
