package de.tum.cit.aet.hephaestus.agent.job;

/**
 * Tracks whether agent job results were delivered to the git provider.
 *
 * <p>This is separate from {@link AgentJobStatus} which models the container
 * execution lifecycle. A job can be {@code COMPLETED} (container finished
 * successfully) but delivery can be {@code FAILED} (e.g., rate limit hit
 * when posting the PR comment).
 */
/**
 * The roll-up of one job's delivery phase, not a machine of its own: PENDING while the phase has not
 * concluded, DELIVERED once every egress intent reached a non-failed terminal, FAILED when the phase
 * concluded with an intent lost. It spans job types that have no dispatch rows at all, which is why it
 * cannot simply be read off {@code feedback_dispatch}.
 */
public enum DeliveryStatus {
    /** Delivery has not been attempted yet. */
    PENDING,
    /** Results were successfully posted to the git provider. */
    DELIVERED,
    /** Delivery failed (rate limit, API error, etc.). */
    FAILED,
}
