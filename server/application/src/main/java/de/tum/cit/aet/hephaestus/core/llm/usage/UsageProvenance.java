package de.tum.cit.aet.hephaestus.core.llm.usage;

/** Which source supplied the whole token vector; acceptance is recorded separately by {@link MeasurementVerification}. */
public enum UsageProvenance {
    /** Every bucket came from the runner's own report, even when the sources were incomparable. */
    RUNNER,

    /**
     * Every bucket came from the proxy's per-call accumulation: it covered the runner's totals, or the two were
     * incomparable and the row is unverifiable.
     */
    PROXY,

    /** Per-bucket maxima from both usage sources, without whole-vector provenance. */
    MERGED,

    /** Neither source had tokens. The row is UNPRICED: this is an admission, not a $0. */
    NONE,
}
