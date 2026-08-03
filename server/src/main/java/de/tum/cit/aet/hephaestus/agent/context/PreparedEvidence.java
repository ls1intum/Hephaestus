package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PreparedEvidence(Map<String, byte[]> files, ArtifactSourceManifest manifest) {
    public PreparedEvidence {
        files = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(files, "files")));
        Objects.requireNonNull(manifest, "manifest");
    }
}
