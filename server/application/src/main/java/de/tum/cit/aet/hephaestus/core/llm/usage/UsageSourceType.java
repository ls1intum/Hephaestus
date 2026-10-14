package de.tum.cit.aet.hephaestus.core.llm.usage;

import java.util.EnumSet;
import java.util.Set;

public enum UsageSourceType {
    REVIEW_JOB,
    MENTOR_TURN,
    PRACTICE_SCRIPT_DECISION,
    /**
     * The embedding model calls of one review attempt's practice scripts; the source is the job.
     */
    PRACTICE_SCRIPT_EMBEDDING,
    /**
     * The reranking model calls of one review attempt's practice scripts; the source is the job.
     */
    PRACTICE_SCRIPT_RERANKING;

    /**
     * The ledger rows of a review attempt's practice scripts: every model kind they call, never the
     * review's own.
     */
    public static final Set<UsageSourceType> PRACTICE_SCRIPT =
            EnumSet.of(PRACTICE_SCRIPT_DECISION, PRACTICE_SCRIPT_EMBEDDING, PRACTICE_SCRIPT_RERANKING);
}
