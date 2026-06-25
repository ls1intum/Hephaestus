package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The destination class a {@link Feedback} unit is delivered on. Decouples “what we say” from “where it lands”
 * so the same synthesised unit can be routed to different rendering channels.
 *
 * <p>Every channel is <b>developer-facing</b>: a unit is delivered to the developer the feedback is about,
 * never to a mentor, instructor, or grader. The system has no facilitator/evaluative delivery channel.
 */
public enum FeedbackChannel {
    /** Placed directly on the work artifact (PR summary / inline note, issue comment). */
    IN_CONTEXT,
    /** A turn in an ongoing mentor conversation with the recipient. */
    CONVERSATION,
    /** Aggregated onto the recipient's private profile. */
    PROFILE,
}
