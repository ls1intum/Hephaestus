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
     * <p>Kept apart from {@link #MANUAL} even though an admin triggers both: a backfill's corpus is
     * selected with hindsight, and folding it into the requested-by-hand population would destroy the
     * health measurement this column exists for.
     */
    BACKFILL,

    /**
     * A recurring schedule looked at recent work again, whether or not anything announced it.
     *
     * <p>Kept apart from {@link #BACKFILL}, whose corpus is chosen with hindsight rather than bounded by
     * rule, and from {@link #EVENT}, so a live-delivery gap stays visible instead of being absorbed into
     * the sweep's own numbers.
     */
    SWEEP,
}
