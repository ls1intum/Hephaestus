package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * One practice's candidate result, as {@link CandidateParser} admitted its shape: one outcome and only the parts that
 * outcome may carry. Citations, warrants, open questions, the example and the coverage stay JSON objects, each a
 * private copy, because admission verifies and annotates the citations in place before it stores them.
 */
public sealed interface Candidate permits MetCandidate, NotMetCandidate, NotApplicableCandidate, UndeterminedCandidate {

    Outcome outcome();

    /** One line. */
    String summary();

    String explanation();

    /** The result's own citations; empty only on {@link NotMetCandidate}, whose problems carry theirs. */
    List<ObjectNode> citations();

    /** {@code {source: "host" | "claimed", …, complete, omitted?}}. */
    ObjectNode coverage();

    default List<CandidateProblem> problems() {
        return List.of();
    }

    default @Nullable ObjectNode example() {
        return null;
    }

    default @Nullable ObjectNode search() {
        return null;
    }

    default @Nullable ObjectNode inapplicability() {
        return null;
    }

    /** The located open questions, {@code {openQuestion, wouldSettleIt, citation?}}. */
    default List<ObjectNode> openQuestions() {
        return List.of();
    }

    /** The severity to store: on {@link NotMetCandidate} the declared one, or else its worst problem's. */
    default @Nullable Severity severity() {
        return null;
    }
}
