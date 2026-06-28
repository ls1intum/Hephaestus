package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Who authored a {@link Feedback} unit's content — provenance for quality measurement.
 *
 * <p>Every unit's CONTENT is currently authored by the LLM delivery agent: the policy floor and the volume
 * cap only DROP / SUPPRESS agent-authored findings (see {@code PolicyFloorSelector}), they never synthesise a
 * substitute body, and there is no fallback content generator. The former {@code POLICY_FLOOR} / {@code FALLBACK}
 * values had zero producers and were removed; re-introduce one only when an actual non-agent author of feedback
 * CONTENT exists.
 */
public enum FeedbackSource {
    /** Synthesised by the LLM delivery agent. */
    AGENT,
}
