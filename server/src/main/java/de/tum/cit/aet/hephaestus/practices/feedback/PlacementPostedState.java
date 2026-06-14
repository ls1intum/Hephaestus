package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The lifecycle of a {@link FeedbackPlacement} against its external delivery surface
 * (a posted comment, diff note, or conversation turn).
 *
 * <ul>
 *   <li>{@link #PENDING} — composed but not yet posted.</li>
 *   <li>{@link #POSTED} — posted at the intended anchor.</li>
 *   <li>{@link #SNAPPED} — posted, but the anchor was snapped to the nearest valid diff line.</li>
 *   <li>{@link #FELL_BACK} — could not anchor inline; posted as a summary/thread comment instead.</li>
 *   <li>{@link #OUTDATED} — posted, but the underlying diff line has since changed.</li>
 *   <li>{@link #ORPHANED} — the anchored code/thread no longer exists.</li>
 *   <li>{@link #GONE} — the external comment/note was deleted out-of-band.</li>
 *   <li>{@link #FAILED} — posting failed terminally.</li>
 * </ul>
 */
public enum PlacementPostedState {
    /** Composed but not yet posted. */
    PENDING,
    /** Posted at the intended anchor. */
    POSTED,
    /** Posted, but the anchor was snapped to the nearest valid diff line. */
    SNAPPED,
    /** Could not anchor inline; posted as a summary/thread comment instead. */
    FELL_BACK,
    /** Posted, but the underlying diff line has since changed. */
    OUTDATED,
    /** The anchored code/thread no longer exists. */
    ORPHANED,
    /** The external comment/note was deleted out-of-band. */
    GONE,
    /** Posting failed terminally. */
    FAILED,
}
