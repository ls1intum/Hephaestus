package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * What became of a recorded signal.
 *
 * <p>Deliberately a machine rather than a {@code triggered} boolean. Submission can be refused
 * <em>after</em> the signal is recorded — the workspace went inactive, the binding was disabled, the
 * budget ran out — and with a boolean such a signal is consumed forever while the unique constraint
 * guarantees nothing ever retries it. That is a review lost permanently and silently.
 * {@link #PENDING} plus the reaper is the repair, and every other value exists so that "why did
 * nothing happen?" has an answer that can be queried instead of guessed.
 */
public enum SignalState {
    /** Observed. No decision has been taken — either none was due, or one is still owed. */
    RECORDED,

    /** A review job was created for it. */
    TRIGGERED,

    /** We decided not to review, and would decide the same way again. Terminal. */
    SUPPRESSED,

    /** We wanted to review and could not yet. The reaper re-offers it until the blocker clears. */
    PENDING,

    /** Pending outlived its usefulness. Terminal, and deliberately distinguishable from suppressed. */
    LAPSED,
}
