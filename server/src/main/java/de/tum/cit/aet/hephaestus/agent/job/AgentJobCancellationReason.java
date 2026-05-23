package de.tum.cit.aet.hephaestus.agent.job;

/**
 * Why an {@link AgentJob} entered the {@link AgentJobStatus#CANCELLED} terminal state. Persisted
 * to {@code agent_job.cancellation_reason} alongside the status transition so incident review
 * can distinguish a graceful drain timeout from an immediate worker shutdown.
 */
public enum AgentJobCancellationReason {
    /** Worker drain budget honored — graceful await timed out and the in-flight job was cancelled. */
    DRAIN_GRACEFUL,
    /** Worker drain mode set to immediate (timeout=0); jobs are cancelled without waiting. */
    DRAIN_IMMEDIATE,
}
