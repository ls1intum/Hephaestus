package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The destination-class axis of a {@link Feedback} unit: <em>where</em> it lands, decoupled from <em>what we say</em>,
 * so the same synthesised unit can be routed to different rendering surfaces.
 *
 * <p>Every channel is <b>developer-facing</b>: a unit is delivered to its recipient developer
 * ({@link Feedback#getRecipientUserId()}), never to a mentor, instructor, or grader. The system has no
 * facilitator/evaluative delivery channel.
 *
 * <p>Constrained at the DB by {@code chk_feedback_channel} (the {@code PROFILE} value is the ADR-0022 §5
 * rename of the dropped {@code REFLECTION_DASHBOARD}).
 *
 * <p>This is the destination a delivery is recorded against, not the thing that does the delivering:
 * the vendor pipes are {@code integration.core.spi.SummaryChannel}, {@code InlineFindingChannel} and
 * {@code ApprovalChannel}, each named for what it posts. Both in-context lanes collapse to
 * {@link #IN_CONTEXT} here, because the ledger records that the developer was told in place while the
 * delivery path still has to know which bean to call.
 */
public enum FeedbackChannel {
    /** Placed directly on the work artifact (PR summary / inline note, issue comment). */
    IN_CONTEXT,
    /** A turn in an ongoing mentor conversation with the recipient. */
    CONVERSATION,
    /** Aggregated onto the recipient's private profile / reflection dashboard. */
    PROFILE,
}
