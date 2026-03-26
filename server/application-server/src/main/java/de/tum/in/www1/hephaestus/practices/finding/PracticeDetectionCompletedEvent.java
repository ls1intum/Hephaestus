package de.tum.in.www1.hephaestus.practices.finding;

import java.util.UUID;

/**
 * Published after practice findings are persisted for a completed agent job.
 *
 * <p>Carries only scalar/immutable data, safe for {@code @Async}
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} subscribers.
 *
 * @param agentJobId      the agent job that produced these findings
 * @param workspaceId     the workspace context
 * @param targetType      the target entity type (e.g., "pull_request")
 * @param targetId        the target entity ID
 * @param contributorId   the contributor whose work was evaluated
 * @param findingsInserted number of new findings persisted
 * @param findingsDiscarded number of findings discarded (unknown slug, over cap, duplicate)
 * @param hasNegative     whether any NEGATIVE verdict findings were present in agent output
 */
public record PracticeDetectionCompletedEvent(
    UUID agentJobId,
    Long workspaceId,
    String targetType,
    Long targetId,
    Long contributorId,
    int findingsInserted,
    int findingsDiscarded,
    boolean hasNegative
) {}
