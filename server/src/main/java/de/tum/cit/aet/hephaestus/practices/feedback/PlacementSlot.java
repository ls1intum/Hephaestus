package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Where a {@code FeedbackPlacement} renders a feedback unit on its delivery surface.
 *
 * <ul>
 *   <li>{@link #SUMMARY} — the PR/MR/issue summary body (one per feedback unit, typically).</li>
 *   <li>{@link #INLINE} — anchored to a specific diff location (file + line/range).</li>
 *   <li>{@link #CONVERSATION_TURN} — a turn in a mentor/facilitator conversation thread.</li>
 * </ul>
 */
public enum PlacementSlot {
    /** Rendered in the PR/MR/issue summary body. */
    SUMMARY,
    /** Anchored to a specific diff location. */
    INLINE,
    /** A turn in a conversation thread. */
    CONVERSATION_TURN,
}
