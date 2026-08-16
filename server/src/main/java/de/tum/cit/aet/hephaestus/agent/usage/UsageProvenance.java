package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Where a ledger row's token counts came from.
 *
 * <p>Two independent records of the same spend exist and neither is complete. The runner's
 * {@code usage.json} is derived by walking the agent session's surviving messages, so compaction — on
 * by default for every session — silently deletes the tokens of every call it drops. The LLM proxy
 * counts each forwarded HTTP call as it happens and never forgets one, but it does not see streamed
 * calls at all, and no OpenAI-compatible usage block it parses reports a cache write.
 *
 * <p>A row is therefore billed per bucket from whichever source saw more, and this says which that was.
 * Without it the amount on a row cannot be interpreted at all: {@link #MERGED} rows match neither
 * source's own number, and a run of {@link #PROXY} where {@link #RUNNER} is expected is the visible
 * signature of a compaction losing calls — the exact failure that made the ledger read 4× low before
 * the maximum was taken.
 */
public enum UsageProvenance {
    /** Every non-zero bucket came from the runner's own report; the proxy saw no more than it did. */
    RUNNER,

    /** Every non-zero bucket came from the proxy's per-call accumulation, exceeding what the runner claimed. */
    PROXY,

    /** The two disagreed in different directions, so the row is a per-bucket maximum of both. */
    MERGED,

    /** Neither source had tokens. The row is UNPRICED: this is an admission, not a $0. */
    NONE,
}
