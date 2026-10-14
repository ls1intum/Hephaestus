package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import org.jspecify.annotations.Nullable;

public record CitationToCheck(
        int index,
        String source,
        String path,
        CitationSide side,
        @Nullable String revision,
        int startLine,
        int endLine,
        String quote) {}
