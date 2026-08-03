package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ArtifactSourceManifest(
    SourceContractVersion contractVersion,
    String catalogDigest,
    EvidenceProfileId profileId,
    Instant capturedAt,
    List<SourceCapture> sources,
    List<EvidenceViewTransformation> viewTransformations
) {
    public ArtifactSourceManifest {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(catalogDigest, "catalogDigest");
        if (!catalogDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid catalog digest: " + catalogDigest);
        }
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        viewTransformations = List.copyOf(Objects.requireNonNull(viewTransformations, "viewTransformations"));
        var kinds = new HashSet<SourceKind>();
        for (SourceCapture source : sources) {
            if (!kinds.add(source.kind())) {
                throw new IllegalArgumentException("Duplicate source capture: " + source.kind());
            }
        }
    }
}
