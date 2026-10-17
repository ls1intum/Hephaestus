package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The practice is met: complete coverage, at least one citation, an optional example and search warrant. */
public record MetCandidate(
        String summary,
        String explanation,
        List<EvidenceCitation> citations,
        ResultCoverage coverage,
        @Nullable ResultExample example,
        @Nullable SearchWarrant search)
        implements Candidate {
    public MetCandidate {
        citations = List.copyOf(citations);
    }

    @Override
    public ResultOutcome outcome() {
        return ResultOutcome.MET;
    }
}
