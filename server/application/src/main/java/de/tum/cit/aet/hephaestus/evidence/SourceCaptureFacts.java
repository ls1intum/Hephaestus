package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What is true of this capture and could not be known before it ran.
 *
 * <p>Holds nothing derivable from the catalog the manifest pins by digest — a copied constant can drift
 * from its source. The timestamps are nullable so a collector that could not establish one records that
 * it could not, rather than omitting the fact.
 */
public record SourceCaptureFacts(
        Instant capturedAt,
        @Nullable Instant sourceEffectiveAt,
        @Nullable Instant observedAt,
        @Nullable String immutableIdentity) {
    public SourceCaptureFacts {
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (immutableIdentity != null && immutableIdentity.isBlank()) {
            throw new IllegalArgumentException("immutableIdentity must be null or non-blank");
        }
    }
}
