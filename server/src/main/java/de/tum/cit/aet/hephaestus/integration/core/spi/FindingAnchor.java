package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

/**
 * Anchor for an inline finding. Sealed so new variants force every consumer to handle
 * them at compile time. Today only SCM diff coordinates are produced.
 */
public sealed interface FindingAnchor permits FindingAnchor.DiffAnchor {
    /** SCM diff coordinates. {@code side} disambiguates multi-line inline shapes (Bitbucket). */
    record DiffAnchor(
        String filePath,
        int newLineNumber,
        @Nullable Integer startLine,
        DiffSide side
    ) implements FindingAnchor {
        public DiffAnchor(String filePath, int newLineNumber, @Nullable Integer startLine) {
            this(filePath, newLineNumber, startLine, DiffSide.RIGHT);
        }
    }

    enum DiffSide {
        LEFT,
        RIGHT,
        BOTH,
    }
}
