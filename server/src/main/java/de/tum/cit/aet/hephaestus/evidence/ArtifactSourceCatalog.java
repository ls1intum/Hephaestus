package de.tum.cit.aet.hephaestus.evidence;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ArtifactSourceCatalog(SourceContractVersion version, List<ArtifactSourceContract> sources) {
    public ArtifactSourceCatalog {
        Objects.requireNonNull(version, "version");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Artifact source catalog must contain sources");
        }
        indexSources(sources);
    }

    public Optional<ArtifactSourceContract> source(SourceKind kind) {
        return sources
            .stream()
            .filter(source -> source.kind().equals(kind))
            .findFirst();
    }

    /**
     * Every source that declares it applies to this artifact kind — the whole evidence surface a review
     * of that kind can ever see.
     *
     * <p>This used to be stated a second time as a named {@code EvidenceProfile} listing its sources by
     * hand. The two answers were required to agree and nothing made them: a profile could only ever
     * repeat what the sources already say, or be wrong about it. Empty means nothing has declared itself
     * usable for the kind, which callers treat as an unknown kind rather than as a review with no
     * evidence.
     */
    public Set<SourceKind> sourcesFor(String artifactKind) {
        Objects.requireNonNull(artifactKind, "artifactKind");
        Set<SourceKind> kinds = new LinkedHashSet<>();
        for (ArtifactSourceContract source : sources) {
            if (source.appliesTo(artifactKind)) {
                kinds.add(source.kind());
            }
        }
        return Set.copyOf(kinds);
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
