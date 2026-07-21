package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import java.util.Map;

/**
 * Domain-specific handler for a single {@link AgentJobType}.
 *
 * <p>The handler owns ALL domain logic for its job type: extracting submission metadata,
 * preparing workspace context, and delivering results. The executor pipeline and sandbox
 * manager remain completely domain-agnostic.
 *
 * <p>Handlers are registered in {@link de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry}
 * and looked up by {@link AgentJobType}. Handlers are plain objects with constructor-injected
 * dependencies — no Spring annotations on the interface or its methods.
 *
 * <h2>Lifecycle (called by executor)</h2>
 * <ol>
 *   <li>{@link #createSubmission} — event listener extracts metadata + idempotency key</li>
 *   <li>{@link #prepareInputFiles} — populate workspace files (including {@code task.json}) before container start</li>
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
     * Prepare all files the agent needs in its workspace, including the
     * {@link de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope} at {@code /workspace/task.json}.
     *
     * <p>Returns a map of relative paths to file contents. These are injected into the
     * container's {@code /workspace} directory via the sandbox's tar injection mechanism.
     *
     * @param job the persisted job (metadata is available via {@link AgentJob#getMetadata()})
     * @return workspace files (relative path → content)
     * @throws JobPreparationException if context preparation fails
     */
    Map<String, byte[]> prepareInputFiles(AgentJob job);

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

    /**
     * Best-effort dedup check for delivery recovery (#1368 hardening): has a delivery for THIS job
     * already landed at the provider, even though {@code deliveryCommentId} was never persisted? This
     * covers the crash window between {@link #deliver} actually posting a comment and the caller
     * persisting its id — without this check, a delivery-recovery retry (see
     * {@code AgentJobZombieSweeper#recoverStuckDeliveries}) would blindly call {@link #deliver} again and
     * post a duplicate.
     *
     * <p><b>Tri-state, not {@code Optional} (#1368 fix wave, finding #6).</b> The caller ({@code
     * AgentJobLifecycleService#recoverStuckDelivery}) treats the three outcomes very differently: {@link
     * ExistingDeliveryLookup.Kind#FOUND} records the found comment id as delivered WITHOUT re-posting;
     * {@link ExistingDeliveryLookup.Kind#ABSENT} proceeds to a normal {@link #deliver} attempt (confirmed
     * safe to post); {@link ExistingDeliveryLookup.Kind#UNKNOWN} does NEITHER — it leaves the delivery
     * {@code PENDING} for a later recovery pass rather than guessing, since guessing wrong in the "post"
     * direction risks a duplicate on exactly the crash-recovery path this exists to protect. Collapsing
     * these into an {@code Optional} (as before this fix) made "could not determine" indistinguishable
     * from "confirmed absent" — every lookup failure silently fell through to re-posting.
     *
     * <p>Default {@code UNKNOWN} — a handler whose delivery channel supports searching for the embedded
     * job marker overrides this with a real {@code FOUND}/{@code ABSENT}/{@code UNKNOWN} answer; one that
     * can't (or doesn't post externally at all, e.g. conversation review) leaves the default, and the
     * caller never auto-reposts for it — only ever records a confirmed match or exhausts the recovery
     * attempt cap.
     */
    default ExistingDeliveryLookup findExistingDelivery(AgentJob job) {
        return ExistingDeliveryLookup.unknown();
    }

    /**
     * Provide host volume mounts for the sandbox container.
     *
     * <p>Returns a map of host paths to container paths. All mounts are read-only
     * (enforced by the sandbox security policy). This allows handlers to mount
     * real git repositories into the container for rich context.
     *
     * <p>Default implementation returns an empty map (no volume mounts).
     *
     * @param job the persisted job
     * @return volume mounts (host path → container path)
     */
    default Map<String, String> volumeMounts(AgentJob job) {
        return Map.of();
    }
}
