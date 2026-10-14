package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The practice is not met: one or more problems, each with its own citations and basis.
 *
 */
public record NotMetCandidate(
        String summary,
        String explanation,
        List<EvidenceCitation> citations,
        ResultCoverage coverage,
        List<CandidateProblem> problems,
        @Nullable ResultExample example,
        @Nullable SearchWarrant search,
        List<OpenQuestion> openQuestions)
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
