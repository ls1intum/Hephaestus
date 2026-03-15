package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import java.util.List;
import java.util.Map;

/**
 * Security hardening profile for a sandboxed container.
 *
 * @param runtime            OCI runtime ({@code null} = default runc, {@code "runsc"} = gVisor)
 * @param seccompProfile     classpath resource path to the seccomp JSON profile
 * @param readOnlyRootfs     mount root filesystem as read-only
 * @param noNewPrivileges    prevent privilege escalation via setuid/setgid
 * @param cgroupnsPrivate    use private cgroup namespace
 * @param ipcMode            IPC namespace mode ({@code "none"} or {@code "private"})
 * @param dropCapabilities   Linux capabilities to drop (typically {@code ["ALL"]})
 * @param tmpfsMounts        writable tmpfs mounts: path → options (e.g. {@code "rw,noexec,nosuid,nodev,size=1g"})
 */
public record SecurityProfile(
    String runtime,
    String seccompProfile,
    boolean readOnlyRootfs,
    boolean noNewPrivileges,
    boolean cgroupnsPrivate,
    String ipcMode,
    List<String> dropCapabilities,
    Map<String, String> tmpfsMounts
) {
    /** Production defaults: maximum hardening with standard tmpfs layout. */
    public static final SecurityProfile DEFAULT = new SecurityProfile(
        null,
        "sandbox/agent-seccomp-profile.json",
        true,
        true,
        true,
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
