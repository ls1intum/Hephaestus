package de.tum.in.www1.hephaestus.agent.sandbox;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the interactive (mentor) sandbox subsystem.
 *
 * <p>Bound from {@code hephaestus.mentor.*} in {@code application.yml}. Operator-facing name is
 * "mentor" because that's the only intended consumer (per the #1066 epic); the implementation
 * lives next to the sync sandbox to keep the SPI/impl boundary clean.
 *
 * <h2>Activation</h2>
 *
 * <p>Off by default. Requires {@code hephaestus.sandbox.enabled=true} <em>and</em>
 * {@code hephaestus.mentor.enabled=true} — the interactive path is dark-launched in #1069 and not
 * intended to be wired up by any production caller until #1071.
 *
 * <h2>Multi-replica</h2>
 *
 * <p>The session registry is in-process. In multi-replica deployments without session-affinity
 * routing (see #1077), reconnects may land on a replica that does not hold the session.
 * {@code deployment.replica-count} is informational only — when {@code > 1} and mentor is
 * enabled, the registry logs a startup WARN.
 *
 * @param enabled whether the interactive sandbox subsystem is active
 * @param idleTtlSeconds idle TTL after which the reaper closes a session (typical mentor session
 *     ≤ 10 min; +50% headroom = 15 min)
 * @param graceTimeoutSeconds SIGTERM → SIGKILL grace period (long enough for an in-flight LLM
 *     turn to finish; Pi per-turn budget is 120 s, 25 s grace overlaps typical cleanup). Must be
 *     strictly less than Spring's {@code spring.lifecycle.timeout-per-shutdown-phase} (default
 *     30 s) — the registry's {@code @PreDestroy} reserves a 5-second scheduling slop on top.
 * @param reapIntervalSeconds idle reaper sweep interval
 * @param ringBufferFrames per-session ring buffer capacity (drop-oldest on overflow); ≈ 2 MB per
 *     session at 512 × 4 KB typical frame size
 * @param stdinWriteTimeoutMs fail-fast threshold if Pi's stdin pipe stalls (child not reading)
 * @param sendQueueCapacity bounded writer queue size; {@code send()} rejects when full (the only
 *     reliable backpressure signal to upstream HTTP callers — a timeout alone permits unbounded
 *     queueing under sustained pressure)
 * @param subscriberQueueCapacity per-subscriber bounded queue (drop-oldest on overflow)
 * @param attachFirstFrameTimeoutSeconds upper bound on the time {@code attach()} blocks waiting
 *     for the runner's first stdout frame
 * @param maxSessionsPerUser cap on concurrent attached sessions per user (DOS guard)
 * @param maxSessionsTotal cap on concurrent attached sessions per app-server replica
 * @param replicaCount informational replica count (set via deployment); guards multi-replica
 *     activation until #1077 lands affinity routing
 * @param maxFrameChars upper bound on a single stdout-line length in characters; a runner
 *     emitting a longer line is treated as a stream-level fault and the session terminates with
 *     {@code reason=ERROR}. Default 1 MiB. Without this bound a buggy or hostile runner could
 *     OOM the app-server with one unterminated megabyte line.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor")
public record InteractiveSandboxProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("900") @Min(1) int idleTtlSeconds,
    @DefaultValue("25") @Min(1) int graceTimeoutSeconds,
    @DefaultValue("30") @Min(1) int reapIntervalSeconds,
    @DefaultValue("512") @Min(16) int ringBufferFrames,
    @DefaultValue("5000") @Min(100) int stdinWriteTimeoutMs,
    @DefaultValue("64") @Min(1) int sendQueueCapacity,
    @DefaultValue("64") @Min(1) int subscriberQueueCapacity,
    @DefaultValue("30") @Min(1) int attachFirstFrameTimeoutSeconds,
    @DefaultValue("3") @Min(1) int maxSessionsPerUser,
    @DefaultValue("50") @Min(1) int maxSessionsTotal,
    @DefaultValue("1") @Min(1) int replicaCount,
    @DefaultValue("1048576") @Min(1024) int maxFrameChars
) {}
