package de.tum.in.www1.hephaestus.agent.handler.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Result of {@link JobTypeHandler#createSubmission} — lightweight data to persist on the
 * {@link de.tum.in.www1.hephaestus.agent.job.AgentJob}.
 *
 * @param metadata      domain-specific routing data stored as {@code AgentJob.metadata} (JSONB).
 *                      Schema is defined by the handler — the executor never interprets it.
 * @param idempotencyKey deduplication key (e.g. {@code "pr_review:owner/repo:42:abc123"}).
 *                       Stored in {@code AgentJob.idempotency_key}.
 */
public record JobSubmission(JsonNode metadata, String idempotencyKey) {
    public JobSubmission {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
