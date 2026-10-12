package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * The practice is not met: one or more problems, each with its own citations and basis.
 *
 * @param declaredSeverity the severity the program gave the result, at least its worst problem's; null when it gave
 *     none, and admission stores the worst problem's
 */
public record NotMetCandidate(
        String summary,
        String explanation,
        List<ObjectNode> citations,
        ObjectNode coverage,
        List<CandidateProblem> problems,
        @Nullable Severity declaredSeverity,
        @Nullable ObjectNode example,
        @Nullable ObjectNode search,
        List<ObjectNode> openQuestions)
        implements Candidate {
    public NotMetCandidate {
        citations = List.copyOf(citations);
        problems = List.copyOf(problems);
        openQuestions = List.copyOf(openQuestions);
        if (problems.isEmpty()) throw new IllegalArgumentException("A NOT_MET candidate lists its problems");
    }

    @Override
    public Outcome outcome() {
        return Outcome.NOT_MET;
    }

    @Override
    public Severity severity() {
        return declaredSeverity != null ? declaredSeverity : worst(problems);
    }

    /** The most severe of the problems' severities. */
    public static Severity worst(List<CandidateProblem> problems) {
        return problems.stream()
                .map(CandidateProblem::severity)
                .min(Comparator.comparingInt(Severity::rank))
                .orElseThrow();
    }
}
