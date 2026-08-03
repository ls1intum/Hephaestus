package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EvidenceAssessment(
    SourceKind kind,
    SourceContractVersion policyVersion,
    Instant assessedAt,
    Instant temporalAnchor,
    SourceFreshness freshness,
    boolean acceptable,
    List<String> reasonCodes
) {
    public EvidenceAssessment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(assessedAt, "assessedAt");
        Objects.requireNonNull(temporalAnchor, "temporalAnchor");
        Objects.requireNonNull(freshness, "freshness");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
        if (reasonCodes.stream().anyMatch(code -> code == null || !code.matches("[A-Z][A-Z0-9_]*"))) {
            throw new IllegalArgumentException("Invalid evidence assessment reason code");
        }
        if (!acceptable && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("An unacceptable assessment requires a reason code");
        }
    }
}
