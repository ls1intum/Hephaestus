package de.tum.cit.aet.hephaestus.agent.job;

import java.util.UUID;

/**
 * Projection of an orphaned RUNNING job, so the recovery sweep can requeue it without lazy-loading the
 * {@code Workspace} entity outside a transaction.
 */
public interface OrphanedJobRef {
    UUID getJobId();

    Long getWorkspaceId();

    int getRetryCount();

    /**
     * The worker the job is RUNNING-owned by. Threaded back into the requeue CAS so the sweeper reclaims
     * the row only while it is still owned by the worker it judged dead, never a sibling that has since
     * legitimately re-claimed it.
     */
    String getWorkerId();
}
