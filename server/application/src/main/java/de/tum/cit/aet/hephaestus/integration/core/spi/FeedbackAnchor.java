package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import org.jspecify.annotations.Nullable;

public record FeedbackAnchor(
        String path,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        @Nullable CitationSide side) {
    public FeedbackAnchor {

        if (path.isBlank()
                || (startLine != null && startLine <= 0)
                || (endLine != null && endLine <= 0)
                || (startLine != null && endLine != null && startLine > endLine))
            throw new IllegalArgumentException("Invalid feedback anchor");
    }
}
