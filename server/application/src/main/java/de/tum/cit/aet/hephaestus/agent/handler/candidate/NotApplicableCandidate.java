package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/** The practice does not apply here: at least one citation and the inapplicability warrant. */
public record NotApplicableCandidate(
        String summary, String explanation, List<ObjectNode> citations, ObjectNode coverage, ObjectNode inapplicability)
        implements Candidate {
    public NotApplicableCandidate {
        citations = List.copyOf(citations);
    }

    @Override
    public Outcome outcome() {
        return Outcome.NOT_APPLICABLE;
    }
}
