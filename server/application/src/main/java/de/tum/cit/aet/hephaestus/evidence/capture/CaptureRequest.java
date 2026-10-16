package de.tum.cit.aet.hephaestus.evidence.capture;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** @param scanPaths exact frozen paths permitted for a scan job; null for other captures */
public record CaptureRequest(
        UUID reviewId,
        UUID jobId,
        int attempt,
        Set<SourceKind> sources,
        String sourceContractVersion,
        int scriptContractVersion,
        @Nullable Set<String> scanPaths) {
    public CaptureRequest(
            UUID reviewId,
            UUID jobId,
            int attempt,
            Set<SourceKind> sources,
            String sourceContractVersion,
            int scriptContractVersion) {
        this(reviewId, jobId, attempt, sources, sourceContractVersion, scriptContractVersion, null);
    }

    public CaptureRequest {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(jobId, "jobId");
        sources = Set.copyOf(sources);
        scanPaths = scanPaths == null ? null : Set.copyOf(scanPaths);
        new SourceContractVersion(sourceContractVersion);
        if (attempt < 0) throw new IllegalArgumentException("Attempt must not be negative");
        if (scriptContractVersion != 3) throw new IllegalArgumentException("Unsupported practice script contract");
    }
}
