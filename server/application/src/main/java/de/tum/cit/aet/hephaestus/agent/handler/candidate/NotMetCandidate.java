package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * The practice is not met: one or more problems, each with its own citations and basis.
 *
 */
public record NotMetCandidate(
        String summary,
        String explanation,
        List<ObjectNode> citations,
        ObjectNode coverage,
        List<CandidateProblem> problems,
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
    public ResultOutcome outcome() {
        return ResultOutcome.NOT_MET;
    }
}
