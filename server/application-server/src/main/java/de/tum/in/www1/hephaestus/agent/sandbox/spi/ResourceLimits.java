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
  public ResourceLimits {
    if (memoryBytes <= 0) {
      throw new IllegalArgumentException("memoryBytes must be positive, got: " + memoryBytes);
    }
    if (cpus <= 0) {
      throw new IllegalArgumentException("cpus must be positive, got: " + cpus);
    }
    if (pidsLimit <= 0) {
      throw new IllegalArgumentException("pidsLimit must be positive, got: " + pidsLimit);
    }
    Objects.requireNonNull(maxRuntime, "maxRuntime must not be null");
    if (maxRuntime.isNegative() || maxRuntime.isZero()) {
      throw new IllegalArgumentException("maxRuntime must be positive, got: " + maxRuntime);
    }
  }

  /** Sensible defaults: 4 GB RAM, 2 CPUs, 256 PIDs, 10 min timeout. */
  public static final ResourceLimits DEFAULT =
      new ResourceLimits(4L * 1024 * 1024 * 1024, 2.0, 256, Duration.ofMinutes(10));
}
