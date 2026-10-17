package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;

/**
 * The practice script calls that one attempt made with one kind of model, over every practice. This is the
 * unit that the ledger bills and that the proxy prices and caps: pricing each practice's share alone
 * would round, and clamp to the minimum cost, once per practice.
 *
 * @param inputTokens excludes the cache reads and writes, like the review's own counters
 * @param reportedCalls calls whose provider reported usage, including a reported zero
 */
public record PracticeScriptKindTotal(
        ModelKind modelKind,
        long calls,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long searchUnits,
        long reportedCalls) {

    public boolean hasCompleteUsage() {
        return calls > 0 && reportedCalls == calls;
    }

    /** The tokens that count against the attempt token limit: the whole prompt and the output. */
    public long tokens() {
        return inputTokens + cacheReadTokens + cacheWriteTokens + outputTokens;
    }
}
