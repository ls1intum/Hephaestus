package de.tum.in.www1.hephaestus.agent.job;

import java.util.Objects;
import java.util.UUID;

/**
 * Spring application event published after an {@link AgentJob} is persisted.
 *
 * <p>Consumed by {@link AgentJobSubmitter} via {@code @TransactionalEventListener(AFTER_COMMIT)}
 * to publish the job to NATS JetStream.
 *
 * @param jobId         the persisted job UUID
 * @param workspaceId   workspace ID used as NATS subject token
 */
public record AgentJobCreatedEvent(UUID jobId, Long workspaceId) {
    public AgentJobCreatedEvent {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
    }
}
