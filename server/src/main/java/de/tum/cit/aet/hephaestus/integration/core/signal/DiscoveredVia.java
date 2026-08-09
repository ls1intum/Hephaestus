package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * How we came to know a signal happened.
 *
 * <p>Recorded for every signal because it is a free health measurement: a sustained {@link #SYNC} rate
 * on a signal that should arrive live means something upstream is misconfigured.
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
     * <p>Kept apart from {@link #MANUAL} even though an admin asked for both: a backfill is a bulk sweep
     * whose corpus was selected with hindsight, and folding its rows into the requested-by-hand
     * population would destroy the health measurement this column exists for.
     */
    BACKFILL,

    /**
     * A recurring schedule looked at recent work again, whether or not anything announced it.
     *
     * <p>Kept apart from {@link #BACKFILL} even though both arrive through a campaign, and apart from
     * {@link #EVENT} even though both measure the population events measure. Against BACKFILL because a
     * sweep's corpus is bounded to the recent past by rule rather than chosen with hindsight, so its
     * measurements belong in the live trend line and a campaign's do not. Against EVENT because that
     * column answers "did the provider tell us", and a sustained gap between what a sweep finds and what
     * events announced is exactly the delivery failure it exists to expose.
     */
    SWEEP,
}
