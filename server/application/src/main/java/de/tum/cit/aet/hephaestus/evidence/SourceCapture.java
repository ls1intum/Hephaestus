package de.tum.cit.aet.hephaestus.evidence;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SourceCapture(SourceKind kind, SourceCaptureState state, List<CapturedSource> files) {
    public SourceCapture {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        if (new HashSet<>(files.stream().map(CapturedSource::path).toList()).size() != files.size()) {
            throw new IllegalArgumentException("Source capture cannot repeat an file path: " + kind);
        }
        if (!(state instanceof SourceCaptureState.Available) && !files.isEmpty()) {
            throw new IllegalArgumentException("Unavailable source cannot contain files: " + kind);
        }
        if (state instanceof SourceCaptureState.Available available
                && available.content() == SourceContentState.NON_EMPTY
                && files.isEmpty()) {
            throw new IllegalArgumentException("Non-empty source must contain at least one file: " + kind);
        }
    }
}
