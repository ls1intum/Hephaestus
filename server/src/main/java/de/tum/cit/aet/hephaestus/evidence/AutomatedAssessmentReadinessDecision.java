package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record AutomatedAssessmentReadinessDecision(
    String practiceSlug,
    Instant decidedAt,
    boolean ready,
    List<AutomatedAssessmentReadinessReason> reasonCodes,
    List<SourceReadinessCheck> sourceChecks
) {
    public AutomatedAssessmentReadinessDecision {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        if (practiceSlug.isBlank()) {
            throw new IllegalArgumentException("practiceSlug must not be blank");
        }
        Objects.requireNonNull(decidedAt, "decidedAt");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
        if (new HashSet<>(reasonCodes).size() != reasonCodes.size()) {
            throw new IllegalArgumentException("reasonCodes must not contain duplicates");
        }
        sourceChecks = List.copyOf(Objects.requireNonNull(sourceChecks, "sourceChecks"));
        if (sourceChecks.isEmpty() && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("a readiness decision needs source readiness checks or a skip reason");
        }
        if (
            new HashSet<>(sourceChecks.stream().map(SourceReadinessCheck::sourceKind).toList()).size() !=
            sourceChecks.size()
        ) {
            throw new IllegalArgumentException("sourceChecks must contain each source kind at most once");
        }
        boolean allMeetRequirements = sourceChecks.stream().allMatch(SourceReadinessCheck::meetsRequirements);
        if (ready != (reasonCodes.isEmpty() && allMeetRequirements)) {
            throw new IllegalArgumentException(
                "ready must equal whether the practice and every source readiness check meet their requirements"
            );
        }
    }
}
