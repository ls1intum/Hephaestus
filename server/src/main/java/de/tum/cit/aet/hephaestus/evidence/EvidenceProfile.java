package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;
import java.util.Set;

/** Named allow-list of source kinds available to one artifact-review profile. */
public record EvidenceProfile(
    EvidenceProfileId id,
    SourceContractVersion version,
    String artifactType,
    Set<SourceKind> allowedSources
) {
    public EvidenceProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(artifactType, "artifactType");
        if (artifactType.isBlank()) {
            throw new IllegalArgumentException("artifactType must not be blank: " + id);
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
