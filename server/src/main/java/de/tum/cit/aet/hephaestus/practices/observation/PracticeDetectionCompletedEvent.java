package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.UUID;

/**
 * Published after practice observations are persisted for a completed agent job.
 *
 * @param developerId the user the observations are about (ADR-0022 {@code aboutUserId})
 * @param findingsDiscarded number of observations discarded (unknown slug, over cap, duplicate)
 */
public record PracticeDetectionCompletedEvent(
    UUID agentJobId,
    Long workspaceId,
    ArtifactKind artifactKind,
    Long artifactId,
    Long developerId,
    int findingsInserted,
    int findingsDiscarded,
    boolean hasNegative
) {}
