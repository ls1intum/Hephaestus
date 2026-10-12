package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import java.util.Locale;

/**
 * Why a candidate is refused, in the order the rules apply: the first failing rule names it. Mirrors
 * {@code CANDIDATE_REFUSALS} in {@code lib/contract.ts}; {@code CandidateContractSyncTest} keeps both equal.
 */
public enum CandidateRefusal {
    MALFORMED_CANDIDATE,
    OUTCOME_PROBLEM_MISMATCH,
    SEVERITY_OUTCOME_MISMATCH,
    SEVERITY_BELOW_WORST_PROBLEM,
    WARRANT_OUTCOME_MISMATCH,
    RESULT_CITATIONS_REQUIRED,
    MET_COVERAGE_INCOMPLETE,
    COVERAGE_INCONSISTENT,
    EXAMPLE_OUTCOME_MISMATCH,
    EXAMPLE_OVERLAPS_PROBLEM,
    BASIS_CITATION_OUT_OF_RANGE,
    DUPLICATE_PROBLEM_KEY;

    /** The code on the wire and in the job's refusal record. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
