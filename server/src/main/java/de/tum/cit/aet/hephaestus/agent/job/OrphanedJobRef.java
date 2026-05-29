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
}
