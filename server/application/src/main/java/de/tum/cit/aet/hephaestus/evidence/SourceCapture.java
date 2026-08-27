package de.tum.cit.aet.hephaestus.evidence;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SourceCapture(SourceKind kind, SourceCaptureState state, List<SourceArtifact> artifacts) {
    public SourceCapture {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        if (new HashSet<>(artifacts.stream().map(SourceArtifact::path).toList()).size() != artifacts.size()) {
            throw new IllegalArgumentException("Source capture cannot repeat an artifact path: " + kind);
        }
        if (!(state instanceof SourceCaptureState.Available) && !artifacts.isEmpty()) {
            throw new IllegalArgumentException("Unavailable source cannot contain artifacts: " + kind);
        }
        if (state instanceof SourceCaptureState.Available available
                && available.content() == SourceContentState.NON_EMPTY
                && artifacts.isEmpty()) {
            throw new IllegalArgumentException("Non-empty source must contain at least one artifact: " + kind);
        }
    }
}
