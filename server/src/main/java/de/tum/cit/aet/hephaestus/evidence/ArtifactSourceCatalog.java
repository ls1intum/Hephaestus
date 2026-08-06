package de.tum.cit.aet.hephaestus.evidence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ArtifactSourceCatalog(
    SourceContractVersion version,
    List<ArtifactSourceContract> sources,
    List<EvidenceProfile> profiles
) {
    public ArtifactSourceCatalog {
        Objects.requireNonNull(version, "version");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        if (sources.isEmpty() || profiles.isEmpty()) {
            throw new IllegalArgumentException("Artifact source catalog must contain sources and profiles");
        }

        Map<SourceKind, ArtifactSourceContract> sourceIndex = indexSources(sources);
        Map<EvidenceProfileId, EvidenceProfile> profileIndex = new HashMap<>();
        for (EvidenceProfile profile : profiles) {
            if (!version.equals(profile.version())) {
                throw new IllegalArgumentException("Profile version does not match catalog: " + profile.id());
            }
            if (profileIndex.put(profile.id(), profile) != null) {
                throw new IllegalArgumentException("Duplicate evidence profile: " + profile.id());
            }
            for (SourceKind kind : profile.allowedSources()) {
                ArtifactSourceContract source = sourceIndex.get(kind);
                if (source == null) {
                    throw new IllegalArgumentException("Profile references unknown source: " + kind);
                }
                if (!source.appliesTo(profile.artifactKind())) {
                    throw new IllegalArgumentException(
                        "Source " + kind + " is incompatible with profile artifact " + profile.artifactKind()
                    );
                }
            }
        }
    }

    public Optional<ArtifactSourceContract> source(SourceKind kind) {
        return sources
            .stream()
            .filter(source -> source.kind().equals(kind))
            .findFirst();
    }

    public Optional<EvidenceProfile> profile(EvidenceProfileId id) {
        return profiles
            .stream()
            .filter(profile -> profile.id().equals(id))
            .findFirst();
    }

    private static Map<SourceKind, ArtifactSourceContract> indexSources(List<ArtifactSourceContract> sources) {
        Map<SourceKind, ArtifactSourceContract> index = new HashMap<>();
        for (ArtifactSourceContract source : sources) {
            if (index.put(source.kind(), source) != null) {
                throw new IllegalArgumentException("Duplicate source kind: " + source.kind());
            }
        }
        return index;
    }
}
