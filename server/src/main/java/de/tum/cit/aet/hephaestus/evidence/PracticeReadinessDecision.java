package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record PracticeReadinessDecision(
    String practiceSlug,
    Instant decidedAt,
    boolean ready,
    List<PracticeReadinessReason> reasonCodes,
    List<EvidenceAssessment> assessments
) {
    public PracticeReadinessDecision {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        if (practiceSlug.isBlank()) {
            throw new IllegalArgumentException("practiceSlug must not be blank");
        }
        Objects.requireNonNull(decidedAt, "decidedAt");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
        if (new HashSet<>(reasonCodes).size() != reasonCodes.size()) {
            throw new IllegalArgumentException("reasonCodes must not contain duplicates");
        }
        assessments = List.copyOf(Objects.requireNonNull(assessments, "assessments"));
        if (assessments.isEmpty() && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("a readiness decision needs evidence assessments or a refusal reason");
        }
        if (new HashSet<>(assessments.stream().map(EvidenceAssessment::kind).toList()).size() != assessments.size()) {
            throw new IllegalArgumentException("assessments must contain each source kind at most once");
        }
        boolean allAcceptable = assessments.stream().allMatch(EvidenceAssessment::acceptable);
        if (ready != (reasonCodes.isEmpty() && allAcceptable)) {
            throw new IllegalArgumentException(
                "ready must equal whether the practice and every evidence assessment are acceptable"
            );
        }
    }
}
