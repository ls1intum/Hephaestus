package de.tum.cit.aet.hephaestus.integration.core.signal;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Writes to the signal ledger.
 *
 * <p>The port exists so that observing a signal and deciding what to do about it are separable: an
 * ingestion path can record what it saw without knowing, or caring, whether a review is warranted.
 * Recording is unconditional; triggering is policy.
 */
public interface SignalRecorder {
    /**
     * Record that a signal occurred, and answer whether this caller is the one that must act on it.
     *
     * <p>The database settles the race, so exactly one caller is told to act however many observe the
     * same occurrence — which is what makes dedup permanent rather than lasting only as long as a job
     * happens to be in flight.
     *
     * @return true iff this call now owns the signal
     */
    default boolean record(SignalKey key, Instant occurredAt, DiscoveredVia discoveredVia) {
        return record(key, occurredAt, discoveredVia, null);
    }

    /**
     * Record an observation that a named person asked for.
     *
     * <p>Attribution is part of the insert rather than a later update because it is what the per-person
     * request limit counts: a row that is briefly unattributed is a row that limit cannot see, and two
     * asks racing through that window are two asks nothing bounded.
     *
     * @param requestedByUserId the SCM user who asked, or null when nobody did — everything discovered
     *     by an event, a sync or a campaign happened without anyone asking, and claiming a requester for
     *     it would put a person's name on work they never commissioned
     */
    boolean record(SignalKey key, Instant occurredAt, DiscoveredVia discoveredVia, @Nullable Long requestedByUserId);

    /** The signal produced a review. */
    void markTriggered(SignalKey key, UUID jobId);

    /**
     * The signal produced no review. The reason decides whether the ledger keeps offering it — see
     * {@link SignalStateReason#isRetryable()}.
     */
    void markRefused(SignalKey key, SignalStateReason reason);
}
