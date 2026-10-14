package de.tum.cit.aet.hephaestus.evidence.capture;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CaptureRequest(
        UUID reviewId,
        UUID jobId,
        int attempt,
        Set<SourceKind> sources,
        String sourceContractVersion,
        int scriptContractVersion) {
    public CaptureRequest {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(jobId, "jobId");
        sources = Set.copyOf(sources);
        new SourceContractVersion(sourceContractVersion);
        if (attempt < 0) throw new IllegalArgumentException("Attempt must not be negative");
        if (scriptContractVersion != 3) throw new IllegalArgumentException("Unsupported practice script contract");
    }
}
