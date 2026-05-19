package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Sealed shape for sandbox volume mounts.
 *
 * <p>Reserves four variants that map cleanly to Kubernetes volume types and to today's
 * Docker tar-injection mechanism. Adapters dispatch on the sealed interface via an
 * exhaustive {@code switch} — the compiler enforces that adding a future variant fails
 * the build until every adapter handles it.
 *
 * <p>Today's {@code DockerSandboxAdapter} fully implements only {@link HostPathMount}
 * (matching the existing tar-injection behavior). The other three variants throw
 * {@code UnsupportedOperationException} with an explicit message. Real implementations
 * land with the K8s adapter epic (or whenever a consumer of {@link SandboxSpec} actually
 * needs the variant).
 *
 * <p>The {@link SandboxSpec#volumeMounts()} field is currently still
 * {@code Map<String,String>} — equivalent to a list of {@link HostPathMount}. Migrating
 * the field to {@code List<VolumeMount>} touches ~30 callsites and is a follow-up issue.
 * The type system reserves the shape here so the migration is a mechanical sweep when it
 * lands, not a design exercise.
 *
 * @see HostPathMount
 * @see EmptyDirMount
 * @see ConfigMapMount
 * @see SecretMount
 */
public sealed interface VolumeMount permits HostPathMount, EmptyDirMount, ConfigMapMount, SecretMount {

    /** Absolute path inside the container where this mount is materialized. */
    Path containerPath();
}

/**
 * Files materialized from a host directory into the container at {@code containerPath}.
 *
 * <p>Mechanism is adapter-specific:
 * <ul>
 *   <li>Docker today: tar-copy via the Docker API (no actual bind mount)</li>
 *   <li>Kubernetes: {@code hostPath} persistent volume (when the K8s adapter lands)</li>
 * </ul>
 *
 * <p>Semantic: "materialize the host directory's contents into the container at the
 * target path". Use for agent code injection — not for secret material (use
 * {@link SecretMount} for that).
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

/**
 * Ephemeral scratch space mounted at {@code containerPath}. tmpfs-backed where the
 * runtime supports it (Docker {@code --tmpfs}, Kubernetes {@code emptyDir.medium=Memory}).
 *
 * @param sizeLimitBytes maximum bytes; enforced where the runtime supports it, ignored
 *                       with a WARN log otherwise. Must be {@code > 0}.
 */
record EmptyDirMount(Path containerPath, long sizeLimitBytes) implements VolumeMount {
    public EmptyDirMount {
        Objects.requireNonNull(containerPath, "containerPath must not be null");
        if (!containerPath.isAbsolute()) {
            throw new IllegalArgumentException("containerPath must be absolute: " + containerPath);
        }
        if (sizeLimitBytes <= 0) {
            throw new IllegalArgumentException("sizeLimitBytes must be > 0: " + sizeLimitBytes);
        }
    }
}

/**
 * Non-secret config files (provider configs, prompts, instruction templates). Materialized
 * as individual files under {@code containerPath}, one per {@code data} entry (key = file
 * name, value = file content). File mode is 0644. Content MAY appear in DEBUG logs.
 */
record ConfigMapMount(Map<String, String> data, Path containerPath) implements VolumeMount {
    public ConfigMapMount {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(containerPath, "containerPath must not be null");
        if (!containerPath.isAbsolute()) {
            throw new IllegalArgumentException("containerPath must be absolute: " + containerPath);
        }
        data = Map.copyOf(data);
    }
}

/**
 * Secret material (API keys, tokens) materialized at {@code containerPath} with file mode
 * {@code 0400}. {@link #toString()} redacts content so logging never leaks values.
 *
 * <p>Adapters MUST materialize {@code SecretMount} through a code path physically
 * separate from the {@code inputFiles} path on {@link SandboxSpec}. Mixing them risks
 * the secrets ending up in debug logs that print {@code inputFiles} key/size pairs.
 * Enforced by {@code SecretMountLeakTest} (ArchUnit).
 */
record SecretMount(Map<String, String> data, Path containerPath) implements VolumeMount {
    public SecretMount {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(containerPath, "containerPath must not be null");
        if (!containerPath.isAbsolute()) {
            throw new IllegalArgumentException("containerPath must be absolute: " + containerPath);
        }
        data = Map.copyOf(data);
    }

    @Override
    public String toString() {
        return "SecretMount[containerPath=" + containerPath + ", keys=" + data.keySet() + ", values=REDACTED]";
    }
}
