package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record FreshnessPolicy(FreshnessMode mode, @Nullable Long maxAgeSeconds) {
    public FreshnessPolicy {
        Objects.requireNonNull(mode, "mode");
        if (mode == FreshnessMode.MAX_AGE) {
            if (maxAgeSeconds == null || maxAgeSeconds <= 0) {
                throw new IllegalArgumentException("MAX_AGE freshness requires positive maxAgeSeconds");
            }
        } else if (maxAgeSeconds != null) {
            throw new IllegalArgumentException("maxAgeSeconds is only valid for MAX_AGE freshness");
        }
    }

    public boolean supportsCurrentRequirement() {
        return mode != FreshnessMode.NOT_APPLICABLE;
    }
}
