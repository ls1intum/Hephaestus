package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Which side of a unified diff an anchor line belongs to.
 *
 * <p>Used by both {@code anchorSide} (single-line / range-end) and {@code anchorStartSide}
 * (range-start) on a {@link FeedbackPlacement}.
 *
 * <ul>
 *   <li>{@link #OLD} — the left/base side (removed or context lines).</li>
 *   <li>{@link #NEW} — the right/head side (added or context lines).</li>
 * </ul>
 */
public enum PlacementAnchorSide {
    /** The left/base side of the diff. */
    OLD,
    /** The right/head side of the diff. */
    NEW,
}
