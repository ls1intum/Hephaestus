package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

/**
 * Anchor for inline feedback. Sealed so new variants force every consumer to handle
 * them at compile time. Today only SCM diff coordinates are produced.
 */
public sealed interface FeedbackAnchor permits FeedbackAnchor.DiffAnchor {
    /** SCM diff coordinates. {@code side} disambiguates multi-line inline shapes (Bitbucket). */
    record DiffAnchor(
        String filePath,
        int newLineNumber,
        @Nullable Integer startLine,
        DiffSide side
    ) implements FeedbackAnchor {
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
