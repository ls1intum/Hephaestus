package de.tum.cit.aet.hephaestus.agent.handler.candidate;

/**
 * The bounds of a candidate, equal to {@code CANDIDATE_LIMITS} in {@code lib/contract.ts}
 * ({@code CandidateContractSyncTest}). Lengths count UTF-16 code units, as JavaScript and Java strings do.
 */
public final class CandidateLimits {

    public static final int SUMMARY_CHARS = 160;
    public static final int EXPLANATION_CHARS = 10_000;
    public static final int PROBLEM_EXPLANATION_CHARS = 4000;
    public static final int NEXT_STEP_CHARS = 1000;
    public static final int EXAMPLE_NOTE_CHARS = 280;
    public static final int QUOTE_CHARS = 4000;
    public static final int OPEN_QUESTION_CHARS = 500;
    public static final int WARRANT_FIELD_CHARS = 500;
    public static final int RESULT_CITATIONS = 50;
    public static final int CITATIONS_PER_PROBLEM = 20;
    public static final int PROBLEMS = 50;
    public static final int OPEN_QUESTIONS = 20;
    public static final int OMISSIONS = 20;
    public static final int OMISSION_UNITS = 50;
    public static final int CONSULTED_SOURCES = 20;
    public static final int CANDIDATE_BYTES = 131_072;
    public static final int MAX_COUNT = 1_000_000;

    private CandidateLimits() {}
}
