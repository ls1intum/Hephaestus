package de.tum.cit.aet.hephaestus.agent.task;

import java.util.Objects;
import java.util.UUID;

/**
 * Wire envelope written to {@code /workspace/task.json}. Carries one {@link Task} plus shared
 * routing/versioning fields.
 *
 * <p>The Pi runner asserts {@link #SCHEMA_VERSION} on read and rejects mismatches with exit
 * code {@code 42}. Server and runner ship in the same image rebuild, so the envelope contract
 * has no cross-version compatibility window.
 *
 * @param schemaVersion bumped only when an existing kind gains a required field; new kinds do not bump
 * @param jobId         agent-job UUID for correlation with server-side logs
 * @param workspaceId   workspace database id (must be positive)
 * @param task          the polymorphic task payload (serialised with a {@code kind} discriminator)
 */
public record TaskEnvelope(int schemaVersion, UUID jobId, long workspaceId, Task task) {
    /** Current envelope schema version. Bumped only when a required field is added to an existing kind. */
    public static final int SCHEMA_VERSION = 1;

    public TaskEnvelope {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(task, "task");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive, got " + schemaVersion);
        }
        if (workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive, got " + workspaceId);
        }
    }

    /** Build an envelope at the current {@link #SCHEMA_VERSION}. */
    public static TaskEnvelope of(UUID jobId, long workspaceId, Task task) {
        return new TaskEnvelope(SCHEMA_VERSION, jobId, workspaceId, task);
    }
}
