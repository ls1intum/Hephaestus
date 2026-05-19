package de.tum.in.www1.hephaestus.agent.job;

/**
 * State machine for agent job lifecycle.
 *
 * <pre>
 *   QUEUED ──► RUNNING ──► COMPLETED
 *                  │──► FAILED
 *                  │──► TIMED_OUT
 *      │──────────────► CANCELLED (from QUEUED or RUNNING)
 * </pre>
 */
public enum AgentJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELLED;

    /** Returns {@code true} if no further state transitions are possible. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMED_OUT || this == CANCELLED;
    }
}
