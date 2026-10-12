package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** The evidence leaves a real question open: at least one citation, one or more open questions, a search at most. */
public record UndeterminedCandidate(
        String summary,
        String explanation,
        List<ObjectNode> citations,
        ObjectNode coverage,
        @Nullable ObjectNode search,
        List<ObjectNode> openQuestions)
        implements Candidate {
    public UndeterminedCandidate {
        citations = List.copyOf(citations);
        openQuestions = List.copyOf(openQuestions);
        if (openQuestions.isEmpty()) throw new IllegalArgumentException("An UNDETERMINED candidate asks a question");
    }

    @Override
    public Outcome outcome() {
        return Outcome.UNDETERMINED;
    }
}
