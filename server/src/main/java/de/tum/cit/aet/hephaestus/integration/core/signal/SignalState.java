package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * What became of a recorded signal.
 *
 * <p>Deliberately a state machine rather than a {@code triggered} boolean: submission can be refused
 * after recording (workspace inactive, binding disabled, budget exhausted), which a boolean would
 * consume forever with no retry. {@link #PENDING} plus the reaper is the repair, and every other value
 * exists so "why did nothing happen?" has a queryable answer.
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
