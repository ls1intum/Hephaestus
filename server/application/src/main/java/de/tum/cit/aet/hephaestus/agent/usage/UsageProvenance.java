package de.tum.cit.aet.hephaestus.agent.usage;

/** Which observation source supplied a ledger row's per-bucket maxima. */
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
