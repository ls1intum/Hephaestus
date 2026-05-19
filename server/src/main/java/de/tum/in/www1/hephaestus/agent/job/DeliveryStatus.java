package de.tum.in.www1.hephaestus.agent.job;

/**
 * Tracks whether agent job results were delivered to the git provider.
 *
 * <p>This is separate from {@link AgentJobStatus} which models the container
 * execution lifecycle. A job can be {@code COMPLETED} (container finished
 * successfully) but delivery can be {@code FAILED} (e.g., rate limit hit
 * when posting the PR comment).
 */
public enum DeliveryStatus {
    /** Delivery has not been attempted yet. */
    PENDING,
    /** Results were successfully posted to the git provider. */
    DELIVERED,
    /** Delivery failed (rate limit, API error, etc.). */
    FAILED,
}
