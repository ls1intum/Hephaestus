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
}
