package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Sealed shape for sandbox volume mounts. Today's only permitted variant is
 * {@link HostPathMount}, matching the existing Docker tar-injection mechanism. Future
 * variants (EmptyDir, ConfigMap, Secret for the K8s adapter epic) land in the same PR as
 * the first consumer.
 *
 * <p>{@link SandboxSpec#volumeMounts()} is still {@code Map<String, String>}; migrating to
 * {@code List<VolumeMount>} is a mechanical sweep filed alongside the first non-HostPath
 * consumer.
 */
public sealed interface VolumeMount permits HostPathMount {

    /** Absolute path inside the container where this mount is materialized. */
    Path containerPath();
}

/**
 * Files materialized from a host directory into the container at {@code containerPath}.
 * Mechanism is adapter-specific (Docker today: tar-copy; K8s future: hostPath PV). Use
 * for code/artifact injection — not for secret material.
 */
record HostPathMount(Path hostPath, Path containerPath, boolean readOnly) implements VolumeMount {
    public HostPathMount {
        Objects.requireNonNull(hostPath, "hostPath must not be null");
        Objects.requireNonNull(containerPath, "containerPath must not be null");
        if (!hostPath.isAbsolute()) {
            throw new IllegalArgumentException("hostPath must be absolute: " + hostPath);
        }
        if (!containerPath.isAbsolute()) {
            throw new IllegalArgumentException("containerPath must be absolute: " + containerPath);
        }
    }
}
