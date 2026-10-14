package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.List;

/** The practice does not apply here: at least one citation and the inapplicability warrant. */
public record DoesNotApplyCandidate(
        String summary,
        String explanation,
        List<EvidenceCitation> citations,
        ResultCoverage coverage,
        InapplicabilityWarrant inapplicability)
        implements Candidate {
    public DoesNotApplyCandidate {
        citations = List.copyOf(citations);
    }

    @Override
    public ResultOutcome outcome() {
        return ResultOutcome.DOES_NOT_APPLY;
    }
}
