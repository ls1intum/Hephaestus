package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

public record FreshnessPolicy(FreshnessMode mode) {
    public FreshnessPolicy {
        Objects.requireNonNull(mode, "mode");
    }

    public boolean supportsCurrentRequirement() {
        return mode != FreshnessMode.NOT_APPLICABLE;
    }
}
