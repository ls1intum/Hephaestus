package de.tum.cit.aet.hephaestus.integration.core.sync;

import java.util.Set;

/** Lifecycle status of a {@link SyncJob}. */
public enum SyncJobStatus {
    /** Row created, not yet handed to the runner body. */
    PENDING,
    /** Runner body is executing; the job holds the one-active-slot per connection. */
    RUNNING,
    /** Completed with no known issues. */
    SUCCEEDED,
    /** Completed, but a secondary phase failed or reported warnings. */
    SUCCEEDED_WITH_WARNINGS,
    /** The runner body threw, or the job was reaped as abandoned (no heartbeat). */
    FAILED,
    /** Cooperative cancellation completed (the runner observed {@code cancelRequested} and exited early). */
    CANCELLED;

    /** The job still occupies the one-active-slot for its connection. */
    public static final Set<SyncJobStatus> ACTIVE = Set.of(PENDING, RUNNING);

    /** The job has finished one way or another. */
    public static final Set<SyncJobStatus> TERMINAL = Set.of(SUCCEEDED, SUCCEEDED_WITH_WARNINGS, FAILED, CANCELLED);

    /** Terminal AND successful (used for {@code lastSuccessfulSyncAt}). */
    public static final Set<SyncJobStatus> SUCCESSFUL = Set.of(SUCCEEDED, SUCCEEDED_WITH_WARNINGS);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
