package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.util.Objects;

/** Attribution and digest of a frozen file, independent of reviewed-work readiness facts. */
public record FolderSource(SourceKind kind, CapturedSource file) {
    public FolderSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(file, "file");
    }
}
