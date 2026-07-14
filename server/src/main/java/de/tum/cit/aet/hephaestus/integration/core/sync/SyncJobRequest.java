package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.jspecify.annotations.Nullable;

/**
 * Parameter object for {@link SyncJobService#beginJob} / {@link SyncJobService#run} — keeps both
 * methods under the repo's 6-parameter ceiling (CodeQualityTest) once the runner body is added.
 */
public record SyncJobRequest(
    long workspaceId,
    long connectionId,
    IntegrationKind kind,
    SyncJobType type,
    SyncJobTrigger trigger,
    @Nullable Long triggeredByUserId
) {}
