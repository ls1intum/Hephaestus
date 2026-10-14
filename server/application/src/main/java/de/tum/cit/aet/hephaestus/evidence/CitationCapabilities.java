package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

public record CitationCapabilities(CitationRequirement side, CitationRequirement commit) {
    public CitationCapabilities {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(commit, "commit");
    }
}
