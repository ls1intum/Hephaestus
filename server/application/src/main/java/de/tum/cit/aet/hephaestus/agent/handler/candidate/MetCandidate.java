package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** The practice is met: complete coverage, at least one citation, an optional example and search warrant. */
public record MetCandidate(
        String summary,
        String explanation,
        List<ObjectNode> citations,
        ObjectNode coverage,
        @Nullable ObjectNode example,
        @Nullable ObjectNode search)
        implements Candidate {
    public MetCandidate {
        citations = List.copyOf(citations);
    }

    @Override
    public Outcome outcome() {
        return Outcome.MET;
    }
}
