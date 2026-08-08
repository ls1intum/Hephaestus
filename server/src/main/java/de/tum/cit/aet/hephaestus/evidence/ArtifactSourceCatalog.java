package de.tum.cit.aet.hephaestus.evidence;

import java.util.ArrayList;
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
     * The sources a practice bound to this kind reads when its author has not said otherwise.
     *
     * <p>A subset of {@link #sourcesFor(String)}: everything applicable is what a review of the kind
     * <em>could</em> see, this is what it starts with. Kept as a fact on each source contract, so a new
     * artifact kind arrives with its starting evidence already stated and nothing has to be told about
     * it separately.
     *
     * <p>In catalog order, which is best-established-first — the order an authoring surface shows as a
     * starting point. Alphabetical would put a comment thread ahead of the artifact it hangs off.
     */
    public List<SourceKind> defaultSourcesFor(String artifactKind) {
        Objects.requireNonNull(artifactKind, "artifactKind");
        List<SourceKind> kinds = new ArrayList<>();
        for (ArtifactSourceContract source : sources) {
            if (source.isDefaultRequirement() && source.appliesTo(artifactKind)) {
                kinds.add(source.kind());
            }
        }
        return List.copyOf(kinds);
    }

    /**
     * Every source that declares it applies to this artifact kind — the whole evidence surface a review
     * of that kind can ever see.
     *
     * <p>Derived from the sources rather than listed a second time by hand: a hand-written list of them
     * could only ever repeat what the sources already say, or be wrong about it. Empty means nothing has
     * declared itself usable for the kind, which callers treat as an unknown kind rather than as a review
     * with no evidence.
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
