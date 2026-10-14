package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

/** A source can declare immutable identity support; capture alone cannot establish staleness. */
public record IdentityPolicy(IdentityMode mode) {
    public IdentityPolicy {
        Objects.requireNonNull(mode, "mode");
    }
}
