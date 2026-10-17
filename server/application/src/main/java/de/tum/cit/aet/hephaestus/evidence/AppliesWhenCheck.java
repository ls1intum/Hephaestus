package de.tum.cit.aet.hephaestus.evidence;

import java.util.List;

/** A proven absence requires every alternative to be false on complete captured evidence. */
public record AppliesWhenCheck(boolean absent, String otherwise, List<Clause> clauses) {
    public AppliesWhenCheck {
        clauses = List.copyOf(clauses);
        if (clauses.isEmpty()
                || otherwise.isBlank()
                || absent != clauses.stream().allMatch(clause -> clause.result() == Result.NOT_FOUND))
            throw new IllegalArgumentException("Applicability requires a reason and a decision for every alternative");
    }

    public record Clause(String aspect, SourceKind readFrom, Result result) {}

    public enum Result {
        FOUND,
        NOT_FOUND,
        UNDECIDABLE
    }
}
