package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PreparedJobInputs(
    Map<String, byte[]> files,
    /** Staged by path so their bytes never enter this process; see {@code EvidenceContribution#filesOnDisk}. */
    Map<String, java.nio.file.Path> filesOnDisk,
    /** Releases the staging directories behind {@link #filesOnDisk} once the sandbox holds the files. */
    java.util.List<AutoCloseable> cleanups,
    @Nullable ArtifactSourceManifest artifactSourceManifest,
    @Nullable AutomatedReviewReadinessReport automatedReviewReadinessReport
) implements AutoCloseable {
    public PreparedJobInputs(
        Map<String, byte[]> files,
        @Nullable ArtifactSourceManifest artifactSourceManifest,
        @Nullable AutomatedReviewReadinessReport automatedReviewReadinessReport
    ) {
        this(files, Map.of(), java.util.List.of(), artifactSourceManifest, automatedReviewReadinessReport);
    }

    @Override
    public void close() {
        for (AutoCloseable cleanup : cleanups) {
            try {
                cleanup.close();
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PreparedJobInputs.class).warn("Could not release staged input", e);
            }
        }
    }

    public PreparedJobInputs {
        files = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(files, "files")));
        filesOnDisk = Map.copyOf(Objects.requireNonNull(filesOnDisk, "filesOnDisk"));
        cleanups = java.util.List.copyOf(Objects.requireNonNull(cleanups, "cleanups"));
        if ((artifactSourceManifest == null) != (automatedReviewReadinessReport == null)) {
            throw new IllegalArgumentException("Evidence manifest and readiness report must be provided together");
        }
    }

    public static PreparedJobInputs filesOnly(Map<String, byte[]> files) {
        return new PreparedJobInputs(files, null, null);
    }
}
