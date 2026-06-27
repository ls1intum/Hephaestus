package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.UUID;

/**
 * Published after practice observations are persisted for a completed agent job.
 *
 * <p>Carries only scalar/immutable data, safe for {@code @Async}
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} subscribers.
 *
 * @param agentJobId      the agent job that produced these observations
 * @param workspaceId     the workspace context
 * @param artifactType      the target entity type (e.g., {@link WorkArtifact#PULL_REQUEST})
 * @param artifactId        the target entity ID
 * @param developerId   the developer whose work was evaluated
 * @param findingsInserted number of new observations persisted
 * @param findingsDiscarded number of observations discarded (unknown slug, over cap, duplicate)
 * @param hasNegative     whether any BAD (problem) observation was present in agent output
 */
public record PracticeDetectionCompletedEvent(
    UUID agentJobId,
    Long workspaceId,
    WorkArtifact artifactType,
    Long artifactId,
    Long developerId,
    int findingsInserted,
    int findingsDiscarded,
    boolean hasNegative
) {}
