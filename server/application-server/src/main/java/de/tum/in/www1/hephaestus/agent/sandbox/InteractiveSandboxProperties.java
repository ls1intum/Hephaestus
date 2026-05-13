package de.tum.in.www1.hephaestus.agent.sandbox;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the interactive (mentor) sandbox. Bound from {@code hephaestus.mentor.*}.
 *
 * @param graceTimeoutSeconds SIGTERM → SIGKILL grace. Must be strictly less than Spring's
 *     {@code spring.lifecycle.timeout-per-shutdown-phase} (default 30 s) — the registry's
 *     {@code @PreDestroy} reserves a 5-second scheduling slop on top.
 * @param sendQueueCapacity bounded writer queue. {@code send()} rejects when full — the only
 *     honest backpressure signal to upstream callers (a timeout alone allows unbounded queueing).
 * @param maxFrameChars upper bound on a single stdout-line length. A longer line is treated as a
 *     stream-level fault and the session terminates {@code ERROR}. Without this bound a hostile
 *     runner could OOM the app-server with one unterminated megabyte line.
 * @param replicaCount informational; guards multi-replica activation (see #1077).
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
