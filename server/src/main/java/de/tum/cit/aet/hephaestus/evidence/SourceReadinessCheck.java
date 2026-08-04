package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SourceReadinessCheck(
    SourceKind sourceKind,
    SourceContractVersion sourceContractVersion,
    Instant checkedAt,
    Instant temporalAnchor,
    SourceFreshness freshness,
    boolean meetsRequirements,
    List<SourceReadinessReason> reasonCodes
) {
    public SourceReadinessCheck {
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(checkedAt, "checkedAt");
        Objects.requireNonNull(temporalAnchor, "temporalAnchor");
        Objects.requireNonNull(freshness, "freshness");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
        if (new HashSet<>(reasonCodes).size() != reasonCodes.size()) {
            throw new IllegalArgumentException("Source readiness reason codes must be unique");
        }
        if (meetsRequirements && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("A source that meets requirements cannot have reason codes");
        }
        if (!meetsRequirements && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("A source that does not meet requirements requires a reason code");
        }
    }
}
