package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable facts recorded at collection time; nullable watermark fields make unknown freshness explicit. */
public record SourceCaptureFacts(
    Instant capturedAt,
    @Nullable Instant sourceEffectiveAt,
    @Nullable Instant observedAt,
    @Nullable String immutableIdentity,
    String queryScope,
    CompletenessBasis completenessBasis,
    RepresentationFidelity representationFidelity
) {
    public SourceCaptureFacts {
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (immutableIdentity != null && immutableIdentity.isBlank()) {
            throw new IllegalArgumentException("immutableIdentity must be null or non-blank");
        }
        Objects.requireNonNull(queryScope, "queryScope");
        if (queryScope.isBlank()) {
            throw new IllegalArgumentException("queryScope must not be blank");
        }
        Objects.requireNonNull(completenessBasis, "completenessBasis");
        Objects.requireNonNull(representationFidelity, "representationFidelity");
    }
}
