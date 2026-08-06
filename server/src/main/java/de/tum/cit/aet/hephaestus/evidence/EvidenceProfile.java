package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;
import java.util.Set;

public record EvidenceProfile(
    EvidenceProfileId id,
    SourceContractVersion version,
    String artifactKind,
    Set<SourceKind> allowedSources
) {
    public EvidenceProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(artifactKind, "artifactKind");
        if (artifactKind.isBlank()) {
            throw new IllegalArgumentException("artifactKind must not be blank: " + id);
        }
        allowedSources = Set.copyOf(Objects.requireNonNull(allowedSources, "allowedSources"));
        if (allowedSources.isEmpty()) {
            throw new IllegalArgumentException("allowedSources must not be empty: " + id);
        }
    }

    public boolean allows(SourceKind kind) {
        return allowedSources.contains(kind);
    }
}
