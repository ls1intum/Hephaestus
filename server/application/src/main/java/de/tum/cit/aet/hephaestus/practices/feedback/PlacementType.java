package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The delivery-surface slot a {@code FeedbackPlacement} occupies — the axis of <em>where</em> a
 * feedback unit renders. Persisted as the NOT NULL {@code placement_type}, value-constrained by
 * {@code chk_feedback_placement_placement}.
 *
 * <ul>
 *   <li>{@link #SUMMARY} — the PR/MR/issue summary body (one per feedback unit, typically).</li>
 *   <li>{@link #INLINE} — anchored to a specific diff location (file + line/range).</li>
 *   <li>{@link #CONVERSATION_TURN} — a turn in a mentor conversation thread.</li>
 * </ul>
 */
public enum PlacementType {
    /** Rendered in the PR/MR/issue summary body. */
    SUMMARY,
    /** Anchored to a specific diff location. */
    INLINE,
    /** A turn in a conversation thread. */
    CONVERSATION_TURN,
}
