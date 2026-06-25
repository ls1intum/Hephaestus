package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Who authored a {@link Feedback} unit's content — provenance for quality measurement, since policy- and
 * fallback-authored units must not be scored as model output.
 */
public enum FeedbackSource {
    /** Synthesised by the LLM delivery agent. */
    AGENT,
    /** Generated deterministically by a policy floor (e.g. a guaranteed-coverage baseline). */
    POLICY_FLOOR,
    /** Produced by a fallback path when synthesis was unavailable or failed. */
    FALLBACK,
}
