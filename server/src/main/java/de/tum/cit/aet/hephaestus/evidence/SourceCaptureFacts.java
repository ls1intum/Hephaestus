package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What is true of this capture and could not be known before it ran.
 *
 * <p>Deliberately holds nothing derivable from the catalog. A manifest pins the exact catalog by
 * digest, so restating a source's selection scope, its representation fidelity, or the basis on which
 * it can claim completeness would copy a constant into every capture of every run — three fields that
 * can drift out of agreement with the catalog they were copied from, and that a reader must then
 * decide between. The nullable watermarks make unknown freshness explicit rather than absent.
 */
public record SourceCaptureFacts(
    Instant capturedAt,
    @Nullable Instant sourceEffectiveAt,
    @Nullable Instant observedAt,
    @Nullable String immutableIdentity
) {
    public SourceCaptureFacts {
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (immutableIdentity != null && immutableIdentity.isBlank()) {
            throw new IllegalArgumentException("immutableIdentity must be null or non-blank");
        }
    }
}
