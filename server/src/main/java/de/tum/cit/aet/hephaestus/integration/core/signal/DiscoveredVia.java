package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * How we came to know a signal happened.
 *
 * <p>Recorded for every signal because it is a free health measurement: a sustained {@link #SYNC}
 * rate on a signal that should arrive live means something upstream is misconfigured, and today that
 * is invisible.
 */
public enum DiscoveredVia {
    /** A provider told us as it happened. */
    EVENT,

    /** We found it while reconciling. We know that it happened, not exactly when. */
    SYNC,

    /** A human asked for the review. */
    MANUAL,

    /**
     * A bounded, confirmed campaign swept artifacts that already existed when it started.
     *
     * <p>Kept apart from {@link #MANUAL} even though an admin asked for both: a backfill is a bulk
     * sweep whose corpus was selected with hindsight, and folding thousands of its rows into the
     * requested-by-hand population would destroy the health measurement this column exists for. It is
     * the discovery counterpart of {@code ObservationOrigin.BACKFILL}, which carries the same split
     * into the measurement layer.
     */
    BACKFILL,
}
