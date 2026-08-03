package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
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
        if (assessments.isEmpty()) {
            throw new IllegalArgumentException("assessments must not be empty");
        }
        if (new HashSet<>(assessments.stream().map(EvidenceAssessment::kind).toList()).size() != assessments.size()) {
            throw new IllegalArgumentException("assessments must contain each source kind at most once");
        }
        boolean allAcceptable = assessments.stream().allMatch(EvidenceAssessment::acceptable);
        if (ready != allAcceptable) {
            throw new IllegalArgumentException("ready must equal whether every evidence assessment is acceptable");
        }
    }
}
