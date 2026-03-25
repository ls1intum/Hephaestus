package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import java.util.List;
import java.util.Map;

/**
 * Security hardening profile for a sandboxed container.
 *
 * <p>Only includes settings that callers can actually influence. Fixed invariants (no-new-privileges,
 * private cgroup namespace, writable rootfs) are hardcoded in {@code ContainerSecurityPolicy} and
 * cannot be changed — they are intentionally absent from this record.
 *
 * <p>The seccomp profile is an infrastructure concern loaded once at startup by {@code
 * DockerSandboxConfiguration} — it is NOT configurable per-execution. All containers share the same
 * seccomp profile for consistent security posture.
 *
 * @param runtime OCI runtime ({@code null} = default runc, {@code "runsc"} = gVisor)
 * @param ipcMode IPC namespace mode ({@code "none"} or {@code "private"})
 * @param dropCapabilities Linux capabilities to drop (typically {@code ["ALL"]})
 * @param tmpfsMounts writable tmpfs mounts: path → options (e.g. {@code
 *     "rw,noexec,nosuid,nodev,size=1g"})
 */
public record SecurityProfile(
    String runtime,
    String ipcMode,
    List<String> dropCapabilities,
    Map<String, String> tmpfsMounts
) {
    /** Production defaults: maximum hardening with standard tmpfs layout. */
    public static final SecurityProfile DEFAULT = new SecurityProfile(
        null,
        "none",
        List.of("ALL"),
        Map.of(
            "/tmp",
            "rw,noexec,nosuid,nodev,size=1073741824",
            "/run",
            "rw,noexec,nosuid,nodev,size=67108864",
            "/home/agent/.local",
            "rw,noexec,nosuid,nodev,size=1073741824"
        )
    );
}
