package de.tum.cit.aet.hephaestus.evidence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record SourceCatalog(SourceContractVersion version, List<SourceContract> sources) {
    public SourceCatalog {
        Objects.requireNonNull(version, "version");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Artifact source catalog must contain sources");
        }
        indexSources(sources);
    }

    public Optional<SourceContract> source(SourceKind kind) {
        return sources.stream().filter(source -> source.kind().equals(kind)).findFirst();
    }

    /**
     * The sources a practice bound to this kind reads when its author has not said otherwise — a subset of
     * {@link #sourcesFor(String)}, the whole applicable evidence surface. In catalog order (best-established
     * first); alphabetical would put a comment thread ahead of the artifact it hangs off.
     */
    public List<SourceKind> defaultSourcesFor(String itemKind) {
        Objects.requireNonNull(itemKind, "itemKind");
        List<SourceKind> kinds = new ArrayList<>();
        for (SourceContract source : sources) {
            if (source.defaultRequirement() && source.appliesTo(itemKind)) {
                kinds.add(source.kind());
            }
        }
        return List.copyOf(kinds);
    }

    /**
     * Every source that declares it applies to this artifact kind. Empty means nothing has declared itself
     * usable for the kind, which callers treat as an unknown kind rather than a review with no evidence.
     */
    public Set<SourceKind> sourcesFor(String itemKind) {
        Objects.requireNonNull(itemKind, "itemKind");
        Set<SourceKind> kinds = new LinkedHashSet<>();
        for (SourceContract source : sources) {
            if (source.appliesTo(itemKind)) kinds.add(source.kind());
        }
        return Set.copyOf(kinds);
    }

    private static Map<SourceKind, SourceContract> indexSources(List<SourceContract> sources) {
        Map<SourceKind, SourceContract> index = new HashMap<>();
        for (SourceContract source : sources) {
            if (index.put(source.kind(), source) != null) {
                throw new IllegalArgumentException("Duplicate source kind: " + source.kind());
            }
        }
        return index;
    }
}
