package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The destination class a {@link Feedback} unit is delivered on. Decouples “what we say” from “where it lands”
 * so the same synthesised unit can be routed to different rendering channels.
 */
public enum FeedbackSurface {
    /** Placed directly on the work artifact (PR summary / inline note, issue comment). */
    IN_CONTEXT,
    /** A turn in an ongoing mentor conversation with the recipient. */
    CONVERSATION,
    /** Aggregated onto the recipient's private reflection dashboard. */
    REFLECTION_DASHBOARD,
    /** Surfaced to a facilitator / instructor rather than the contributor. */
    FACILITATOR,
}
