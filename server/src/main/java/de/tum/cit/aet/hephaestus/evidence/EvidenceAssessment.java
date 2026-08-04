package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record EvidenceAssessment(
    SourceKind kind,
    SourceContractVersion policyVersion,
    Instant assessedAt,
    Instant temporalAnchor,
    SourceFreshness freshness,
    boolean acceptable,
    List<EvidenceAssessmentReason> reasonCodes
) {
    public EvidenceAssessment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(assessedAt, "assessedAt");
        Objects.requireNonNull(temporalAnchor, "temporalAnchor");
        Objects.requireNonNull(freshness, "freshness");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
        if (new HashSet<>(reasonCodes).size() != reasonCodes.size()) {
            throw new IllegalArgumentException("Evidence assessment reason codes must be unique");
        }
        if (acceptable && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("An acceptable assessment cannot have reason codes");
        }
        if (!acceptable && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("An unacceptable assessment requires a reason code");
        }
    }
}
