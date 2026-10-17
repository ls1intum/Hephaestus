package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The evidence leaves a real question open: at least one citation, one or more open questions, a search at most. */
public record CouldNotDetermineCandidate(
        String summary,
        String explanation,
        List<EvidenceCitation> citations,
        ResultCoverage coverage,
        @Nullable SearchWarrant search,
        List<OpenQuestion> openQuestions)
        implements Candidate {
    public CouldNotDetermineCandidate {
        citations = List.copyOf(citations);
        openQuestions = List.copyOf(openQuestions);
        if (openQuestions.isEmpty())
            throw new IllegalArgumentException("A COULD_NOT_DETERMINE candidate asks a question");
    }

    @Override
    public ResultOutcome outcome() {
        return ResultOutcome.COULD_NOT_DETERMINE;
    }
}
