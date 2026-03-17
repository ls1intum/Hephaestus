package de.tum.in.www1.hephaestus.agent.handler.spi;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import java.util.Map;

/**
 * Domain-specific handler for a single {@link AgentJobType}.
 *
 * <p>The handler owns ALL domain logic for its job type: extracting submission metadata,
 * preparing workspace context, building the agent prompt, and delivering results. The
 * executor pipeline and sandbox manager remain completely domain-agnostic.
 *
 * <p>Handlers are registered in {@link de.tum.in.www1.hephaestus.agent.handler.JobTypeHandlerRegistry}
 * and looked up by {@link AgentJobType}. Handlers are plain objects with constructor-injected
 * dependencies — no Spring annotations on the interface or its methods.
 *
 * <h2>Lifecycle (called by executor, issue #746)</h2>
 * <ol>
 *   <li>{@link #createSubmission} — event listener extracts metadata + idempotency key</li>
 *   <li>{@link #prepareInputFiles} — populate workspace files before container start</li>
 *   <li>{@link #buildPrompt} — generate the agent prompt</li>
 *   <li>{@link #deliver} — post-execution result delivery</li>
 * </ol>
 */
public interface JobTypeHandler {
    /** The job type this handler manages. */
    AgentJobType jobType();

    /**
     * Extract lightweight metadata and an idempotency key from a domain event.
     *
     * <p>Called synchronously in the event listener transaction. The returned
     * {@link JobSubmission} is persisted on the {@link AgentJob} before it is queued.
     *
     * @param request type-safe event data (an implementation of {@link JobSubmissionRequest})
     * @return submission data for the new job
     * @throws IllegalArgumentException if the request type does not match this handler
     */
    JobSubmission createSubmission(JobSubmissionRequest request);

    /**
     * Prepare all files the agent needs in its workspace.
     *
     * <p>Returns a map of relative paths to file contents. These are injected into the
     * container's {@code /workspace} directory via the sandbox's tar injection mechanism.
     * For code review handlers this includes repository source files, diffs, and metadata.
     *
     * @param job the persisted job (metadata is available via {@link AgentJob#getMetadata()})
     * @return workspace files (relative path → content)
     * @throws JobPreparationException if context preparation fails
     */
    Map<String, byte[]> prepareInputFiles(AgentJob job);

    /**
     * Build the prompt text for the agent.
     *
     * <p>The executor passes this string to
     * {@link de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest#prompt()}.
     * The adapter decides how to inject it (file, environment variable, CLI argument).
     *
     * @param job the persisted job
     * @return prompt text (must not be blank)
     */
    String buildPrompt(AgentJob job);

    /**
     * Deliver results after successful execution.
     *
     * <p>Called by the executor after the sandbox completes. What "delivery" means is
     * entirely handler-specific: posting a PR comment, sending an email, creating a ticket,
     * updating a dashboard, etc.
     *
     * <p>Default implementation is a no-op. Handlers override when delivery logic is ready
     * (see issue #748).
     *
     * @param job the completed job (output is available via {@link AgentJob#getOutput()})
     */
    default void deliver(AgentJob job) {
        // No-op — overridden by handlers that need result delivery.
    }
}
