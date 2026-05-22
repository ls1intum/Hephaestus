package de.tum.cit.aet.hephaestus.agent.job;

/**
 * Why an {@link AgentJob} entered the {@link AgentJobStatus#CANCELLED} terminal state. Persisted
 * to {@code agent_job.cancellation_reason} alongside the status transition so incident review
 * can distinguish "drain did its job" from "user cancelled" from "execution timed out".
 *
 * <p>Free-text would be cheaper at the column level but loses type-safety at the writer sites —
 * the executor / drain coordinator both call into a single cancel path and can't fail-compile
 * on a typo. Adding values is a one-line enum extension; Liquibase keeps the column as a
 * length-bounded VARCHAR for forward compat.
 */
public enum AgentJobCancellationReason {
    /** Worker drain budget honored — graceful await timed out and the in-flight job was cancelled. */
    DRAIN_GRACEFUL,
    /** Worker drain mode set to immediate (timeout=0); jobs are cancelled without waiting. */
    DRAIN_IMMEDIATE,
    /** User-initiated cancel (admin UI, API). */
    USER,
    /** Per-job execution timeout exceeded; treated as cancellation for delivery semantics. */
    TIMEOUT,
}
