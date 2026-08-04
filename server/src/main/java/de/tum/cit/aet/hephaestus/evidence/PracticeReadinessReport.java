package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record PracticeReadinessReport(
    SourceContractVersion contractVersion,
    String catalogDigest,
    EvidenceProfileId profileId,
    Instant manifestCapturedAt,
    Instant decidedAt,
    List<PracticeReadinessDecision> decisions
) {
    public PracticeReadinessReport {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(catalogDigest, "catalogDigest");
        if (!catalogDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid catalog digest: " + catalogDigest);
        }
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(manifestCapturedAt, "manifestCapturedAt");
        Objects.requireNonNull(decidedAt, "decidedAt");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
        if (decisions.isEmpty()) {
            throw new IllegalArgumentException("A readiness report requires at least one decision");
        }
        if (
            new HashSet<>(decisions.stream().map(PracticeReadinessDecision::practiceSlug).toList()).size() !=
            decisions.size()
        ) {
            throw new IllegalArgumentException("A readiness report cannot repeat a practice");
        }
        if (decisions.stream().anyMatch(decision -> !decision.decidedAt().equals(decidedAt))) {
            throw new IllegalArgumentException("Every readiness decision must use the report decision time");
        }
    }
}
