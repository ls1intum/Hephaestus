package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/** The practice does not apply here: at least one citation and the inapplicability warrant. */
public record DoesNotApplyCandidate(
        String summary, String explanation, List<ObjectNode> citations, ObjectNode coverage, ObjectNode inapplicability)
        implements Candidate {
    public DoesNotApplyCandidate {
        citations = List.copyOf(citations);
    }

    @Override
    public ResultOutcome outcome() {
        return ResultOutcome.DOES_NOT_APPLY;
    }
}
