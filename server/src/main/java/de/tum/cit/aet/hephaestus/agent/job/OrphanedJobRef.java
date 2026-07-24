package de.tum.cit.aet.hephaestus.agent.job;

import java.util.UUID;

/**
 * Projection of an orphaned RUNNING job (#1138) so the orphan-recovery sweep can requeue + re-publish
 * without lazy-loading the {@code Workspace} entity outside a transaction.
 */
public interface OrphanedJobRef {
    UUID getJobId();
    Long getWorkspaceId();
    int getRetryCount();

    /**
     * The (dead) worker id the job is currently RUNNING-owned by. Threaded back into the requeue CAS
     * (#1368 fix wave) so the sweeper only reclaims the row while it is still owned by the worker it
     * identified as dead — never a sibling that has since legitimately re-claimed it.
     */
    String getWorkerId();
}
