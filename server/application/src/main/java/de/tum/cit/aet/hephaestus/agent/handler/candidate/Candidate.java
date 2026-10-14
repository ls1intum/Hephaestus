package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/** One outcome and its admitted evidence, before citation verification. */
public sealed interface Candidate
        permits MetCandidate, NotMetCandidate, DoesNotApplyCandidate, CouldNotDetermineCandidate {

    ResultOutcome outcome();

    /** One line. */
    String summary();

    String explanation();

    /** The result's own citations; empty only on {@link NotMetCandidate}, whose problems carry theirs. */
    List<EvidenceCitation> citations();

    ResultCoverage coverage();

    default List<CandidateProblem> problems() {
        return List.of();
    }

    default @Nullable ResultExample example() {
        return null;
    }

    default @Nullable SearchWarrant search() {
        return null;
    }

    default @Nullable InapplicabilityWarrant inapplicability() {
        return null;
    }

    default List<OpenQuestion> openQuestions() {
        return List.of();
    }

    default List<EvidenceCitation> allCitations() {
        var citations = new ArrayList<>(citations());
        problems().forEach(problem -> citations.addAll(problem.citations()));
        var example = example();
        if (example != null) citations.add(example.citation());
        openQuestions().forEach(question -> {
            var citation = question.citation();
            if (citation != null) citations.add(citation);
        });
        return List.copyOf(citations);
    }

    default Candidate withCitations(UnaryOperator<EvidenceCitation> verified) {
        var citations = citations().stream().map(verified).toList();
        var example = example();
        var verifiedExample =
                example == null ? null : new ResultExample(verified.apply(example.citation()), example.note());
        var questions = openQuestions().stream()
                .map(question -> new OpenQuestion(
                        question.openQuestion(),
                        question.wouldSettleIt(),
                        question.citation() == null ? null : verified.apply(question.citation())))
                .toList();
        return switch (this) {
            case MetCandidate met ->
                new MetCandidate(summary(), explanation(), citations, coverage(), verifiedExample, search());
            case NotMetCandidate notMet ->
                new NotMetCandidate(
                        summary(),
                        explanation(),
                        citations,
                        coverage(),
                        problems().stream()
                                .map(problem -> new CandidateProblem(
                                        problem.key(),
                                        problem.severity(),
                                        problem.summary(),
                                        problem.explanation(),
                                        problem.citations().stream()
                                                .map(verified)
                                                .toList(),
                                        problem.basis(),
                                        problem.nextStep()))
                                .toList(),
                        verifiedExample,
                        search(),
                        questions);
            case DoesNotApplyCandidate doesNotApply ->
                new DoesNotApplyCandidate(
                        summary(), explanation(), citations, coverage(), doesNotApply.inapplicability());
            case CouldNotDetermineCandidate undetermined ->
                new CouldNotDetermineCandidate(summary(), explanation(), citations, coverage(), search(), questions);
        };
    }
}
