package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The content-provenance axis of a {@link Feedback} unit — who authored its body, so policy/fallback units are
 * never scored as model output.
 *
 * <p>Every unit's CONTENT is authored by the LLM delivery agent: the policy floor and the volume cap only
 * DROP / SUPPRESS agent-authored findings (see {@code DeliveryComposer}), they never synthesise a
 * substitute body, and there is no fallback content generator. Add a value only when an actual non-agent
 * author of feedback CONTENT exists.
 *
 * <p>Constrained at the DB by {@code chk_feedback_source} (currently {@code AGENT} only).
 */
public enum FeedbackSource {
    /** Synthesised by the LLM delivery agent. */
    AGENT,
}
