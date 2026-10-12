package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * One located problem of a {@link NotMetCandidate}.
 *
 * @param key the program's stable name for it within the candidate, practice-slug shaped; null when it gave none
 * @param citations at least one
 * @param basis why the problem belongs to this work: {@code introduced}, {@code worsened}, {@code duty} or
 *     {@code state}; every index names one of {@code citations}
 */
public record CandidateProblem(
        @Nullable String key,
        Severity severity,
        String summary,
        String explanation,
        List<ObjectNode> citations,
        ObjectNode basis,
        @Nullable String nextStep) {
    public CandidateProblem {
        citations = List.copyOf(citations);
    }

    public String basisType() {
        return basis.path("type").asString();
    }
}
