package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import java.time.Duration;

/**
 * Resource constraints for a sandboxed container.
 *
 * <p>{@code memoryBytes} includes tmpfs allocations — set it high enough to
 * accommodate both process RSS and tmpfs mounts (default: 4 GB).
 *
 * @param memoryBytes maximum memory in bytes (container + tmpfs combined)
 * @param cpus        CPU limit (e.g. 2.0 = two full cores)
 * @param pidsLimit   maximum number of processes inside the container
 * @param maxRuntime  hard deadline after which the container is killed
 */
public record ResourceLimits(long memoryBytes, double cpus, int pidsLimit, Duration maxRuntime) {
    /** Sensible defaults: 4 GB RAM, 2 CPUs, 256 PIDs, 10 min timeout. */
    public static final ResourceLimits DEFAULT = new ResourceLimits(
        4L * 1024 * 1024 * 1024,
        2.0,
        256,
        Duration.ofMinutes(10)
    );
}
