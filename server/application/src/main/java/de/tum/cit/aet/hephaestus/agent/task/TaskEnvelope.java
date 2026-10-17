package de.tum.cit.aet.hephaestus.agent.task;

import java.util.Objects;
import java.util.UUID;

/** The generic task.json envelope read by image contract 6. */
public record TaskEnvelope(
        int schemaVersion,
        UUID reviewId,
        UUID jobId,
        int attempt,
        String workspaceId,
        String prompt,
        String contextRoot,
        String repositoryRoot,
        String manifest,
        String practiceIndex,
        String compositionRequest,
        String preparedFeedback,
        String practiceScripts) {
    public static final int SCHEMA_VERSION = 6;

    public TaskEnvelope {
        Objects.requireNonNull(reviewId, "reviewId");
        Objects.requireNonNull(jobId, "jobId");
        if (attempt < 0) throw new IllegalArgumentException("Attempt must not be negative");
        Objects.requireNonNull(workspaceId, "workspaceId");
        new Task(prompt);
        new TaskPaths(
                contextRoot,
                repositoryRoot,
                manifest,
                practiceIndex,
                compositionRequest,
                preparedFeedback,
                practiceScripts);
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported task schema version");
        if (!workspaceId.matches("[1-9][0-9]*") || workspaceId.length() > 19) {
            throw new IllegalArgumentException("Workspace id must be a positive decimal id");
        }
        Long.parseLong(workspaceId);
    }

    public static TaskEnvelope of(UUID reviewId, UUID jobId, int attempt, long workspaceId, Task task) {
        var paths = TaskPaths.capturedInputs();
        return new TaskEnvelope(
                SCHEMA_VERSION,
                reviewId,
                jobId,
                attempt,
                Long.toString(workspaceId),
                task.prompt(),
                paths.contextRoot(),
                paths.repositoryRoot(),
                paths.manifest(),
                paths.practiceIndex(),
                paths.compositionRequest(),
                paths.preparedFeedback(),
                paths.practiceScripts());
    }

    public TaskPaths paths() {
        return new TaskPaths(
                contextRoot,
                repositoryRoot,
                manifest,
                practiceIndex,
                compositionRequest,
                preparedFeedback,
                practiceScripts);
    }
}
