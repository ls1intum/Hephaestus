package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ResultCandidate(
        long practiceId,
        long practiceVersionId,
        long itemId,
        String itemVersion,
        @Nullable Long developerId,
        ResultOutcome outcome,
        String summary,
        String explanation,
        ResultEvidence evidence,
        @Nullable ResultExample example,
        ResultCoverage coverage,
        List<ProblemCandidate> problems) {
    public ResultCandidate {
        problems = List.copyOf(problems);
    }
}
