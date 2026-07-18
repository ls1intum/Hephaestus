package de.tum.cit.aet.hephaestus.integration.core.sync;

import java.io.Serial;

/**
 * Thrown by {@link SyncJobService} when a connection already has an active (PENDING/RUNNING)
 * {@link SyncJob} — either the service-level existence check or the partial-unique-index race
 * translated from a {@code DataIntegrityViolationException}.
 *
 * <p>Deliberately NOT {@code @ResponseStatus}-annotated: this is not itself an HTTP error. The manual
 * trigger endpoint's contract is idempotent-absorb (return the existing active job with 200, not an
 * error), so callers catch this and unwrap {@link #activeJob()} rather than letting it propagate to a
 * generic advice handler.
 */
public class SyncJobConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SyncJob activeJob;

    public SyncJobConflictException(SyncJob activeJob) {
        super(
            "Connection " + activeJob.getConnection().getId() + " already has an active sync job: " + activeJob.getId()
        );
        this.activeJob = activeJob;
    }

    public SyncJob activeJob() {
        return activeJob;
    }
}
