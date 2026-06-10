package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

/**
 * How a finding was presented within a {@link FeedbackDelivery} — mirrors the partitioning the PR
 * composer already does (inline diff note vs. summary vs. compact overview). Lets analysis ask
 * "given finding F was delivered INLINE at task level, did the contributor act?".
 */
public enum FindingDisplayRole {
    /** Rendered as an inline diff note at a specific file location. */
    INLINE,
    /** Expanded with full detail in the summary comment (non-inlinable findings). */
    SUMMARY,
    /** Listed compactly (title + location) in the summary, full detail on the diff. */
    OVERVIEW,
}
