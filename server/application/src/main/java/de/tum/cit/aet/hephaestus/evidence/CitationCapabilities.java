package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

public record CitationCapabilities(CitationRequirement side, CitationRequirement revision) {
    public CitationCapabilities {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(revision, "revision");
    }
}
