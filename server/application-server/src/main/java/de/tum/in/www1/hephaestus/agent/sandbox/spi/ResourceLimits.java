package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import java.time.Duration;
import java.util.Objects;

/**
 * Resource constraints for a sandboxed container.
 *
 * <p>{@code memoryBytes} includes tmpfs allocations — set it high enough to accommodate both
 * process RSS and tmpfs mounts (default: 4 GB).
 *
 * @param memoryBytes maximum memory in bytes (container + tmpfs combined)
 * @param cpus CPU limit (e.g. 2.0 = two full cores)
 * @param pidsLimit maximum number of processes inside the container
 * @param maxRuntime hard deadline after which the container is killed
 */
public record ResourceLimits(long memoryBytes, double cpus, int pidsLimit, Duration maxRuntime) {
    /** Maximum memory: 16 GB — prevents callers from requesting unbounded resources. */
    public static final long MAX_MEMORY_BYTES = 16L * 1024 * 1024 * 1024;

    /** Maximum CPUs: 8 cores. */
    public static final double MAX_CPUS = 8.0;

    /** Maximum PIDs: 4096 — more than enough for any agent workload. */
    public static final int MAX_PIDS = 4096;

    /** Maximum runtime: 1 hour. */
    public static final Duration MAX_RUNTIME = Duration.ofHours(1);

    public ResourceLimits {
        if (memoryBytes <= 0) {
            throw new IllegalArgumentException("memoryBytes must be positive, got: " + memoryBytes);
        }
        if (memoryBytes > MAX_MEMORY_BYTES) {
            throw new IllegalArgumentException(
                "memoryBytes exceeds maximum (" + MAX_MEMORY_BYTES + "), got: " + memoryBytes
            );
        }
        if (cpus <= 0) {
            throw new IllegalArgumentException("cpus must be positive, got: " + cpus);
        }
        if (cpus > MAX_CPUS) {
            throw new IllegalArgumentException("cpus exceeds maximum (" + MAX_CPUS + "), got: " + cpus);
        }
        if (pidsLimit <= 0) {
            throw new IllegalArgumentException("pidsLimit must be positive, got: " + pidsLimit);
        }
        if (pidsLimit > MAX_PIDS) {
            throw new IllegalArgumentException("pidsLimit exceeds maximum (" + MAX_PIDS + "), got: " + pidsLimit);
        }
        Objects.requireNonNull(maxRuntime, "maxRuntime must not be null");
        if (maxRuntime.isNegative() || maxRuntime.isZero()) {
            throw new IllegalArgumentException("maxRuntime must be positive, got: " + maxRuntime);
        }
        if (maxRuntime.compareTo(MAX_RUNTIME) > 0) {
            throw new IllegalArgumentException("maxRuntime exceeds maximum (" + MAX_RUNTIME + "), got: " + maxRuntime);
        }
    }

    /** Sensible defaults: 4 GB RAM, 2 CPUs, 256 PIDs, 10 min timeout. */
    public static final ResourceLimits DEFAULT = new ResourceLimits(
        4L * 1024 * 1024 * 1024,
        2.0,
        256,
        Duration.ofMinutes(10)
    );
}
