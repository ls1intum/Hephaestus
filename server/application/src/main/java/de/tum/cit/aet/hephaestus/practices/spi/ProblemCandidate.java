package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ProblemCandidate(
        int position,
        @Nullable String key,
        Severity severity,
        String summary,
        String explanation,
        List<EvidenceCitation> citations,
        ClaimBasis basis,
        @Nullable String nextStep) {
    public ProblemCandidate {
        citations = List.copyOf(citations);
    }
}
