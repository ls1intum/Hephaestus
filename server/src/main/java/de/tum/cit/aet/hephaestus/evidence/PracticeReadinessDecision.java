package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PracticeReadinessDecision(
    String practiceSlug,
    Instant decidedAt,
    boolean ready,
    List<EvidenceAssessment> assessments
) {
    public PracticeReadinessDecision {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        if (practiceSlug.isBlank()) {
            throw new IllegalArgumentException("practiceSlug must not be blank");
        }
        Objects.requireNonNull(decidedAt, "decidedAt");
        assessments = List.copyOf(Objects.requireNonNull(assessments, "assessments"));
        boolean allAcceptable = assessments.stream().allMatch(EvidenceAssessment::acceptable);
        if (ready != allAcceptable) {
            throw new IllegalArgumentException("ready must equal whether every evidence assessment is acceptable");
        }
    }
}
