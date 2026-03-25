package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Translates {@link SecurityProfile}, {@link ResourceLimits}, and {@link NetworkPolicy} into a
 * {@link DockerOperations.HostConfigSpec} for container creation.
 *
 * <p>Applies defense-in-depth hardening with enforcement floors — the following invariants are
 * always applied regardless of the caller-supplied {@link SecurityProfile}:
 *
 * <ul>
 *   <li>{@code --cap-drop=ALL} — drop all Linux capabilities (floor: must include "ALL")
 *   <li>{@code --security-opt=no-new-privileges} — prevent setuid/setgid escalation
 *   <li>{@code --cgroupns=private} — prevent cgroup hierarchy leaks
 *   <li>{@code --ipc=none|private} — close IPC vectors ("host"/"shareable" always rejected)
 *   <li>{@code --dns 0.0.0.0} — block DNS when internet is disabled (CVE-2024-29018)
 *   <li>Mandatory tmpfs with {@code noexec,nosuid,nodev} on /tmp, /run, and /home/agent/.local
 *   <li>Ulimits: nofile={@value #NOFILE_LIMIT}, nproc=pidsLimit, core=0 (no core dumps)
 *   <li>Custom seccomp profile (loaded once at startup)
 *   <li>Optional gVisor runtime ({@code --runtime=runsc})
 * </ul>
 *
 * <p><strong>Root filesystem is writable.</strong> {@code --read-only} is intentionally disabled
 * because the agent process writes to {@code /workspace} at runtime, and {@code docker cp} injects
 * files there before container start. Compensating controls: no-new-privileges, non-root user,
 * dropped capabilities, noexec tmpfs, seccomp.
 */
public class ContainerSecurityPolicy {

    private static final int NOFILE_LIMIT = 1024;
    private static final long NANO_CPUS_PER_CPU = 1_000_000_000L;

    /** IPC modes allowed by the enforcement floor — "host" and "shareable" are always rejected. */
    private static final Set<String> ALLOWED_IPC_MODES = Set.of("none", "private");

    /**
     * Mandatory tmpfs mounts that are always applied. Caller-supplied mounts are merged on top, but
     * these paths always have {@code noexec,nosuid,nodev} enforced.
     */
    private static final Map<String, String> MANDATORY_TMPFS = Map.of(
        "/tmp",
        "rw,noexec,nosuid,nodev,size=1073741824",
        "/run",
        "rw,noexec,nosuid,nodev,size=67108864",
        "/home/agent/.local",
        "rw,noexec,nosuid,nodev,size=1073741824"
    );

    private final SandboxProperties properties;
    private final String seccompProfileJson;

    /**
     * @param properties sandbox configuration
     * @param seccompProfileJson pre-loaded seccomp JSON string (null if no profile)
     */
    public ContainerSecurityPolicy(SandboxProperties properties, String seccompProfileJson) {
        this.properties = properties;
        this.seccompProfileJson = seccompProfileJson;
    }

    /**
     * Build a complete {@link DockerOperations.HostConfigSpec} from the SPI types.
     *
     * @param security security hardening flags
     * @param resources resource limits
     * @param networkPolicy network policy (used for DNS configuration)
     * @return the host config spec for container creation
     */
    public DockerOperations.HostConfigSpec buildHostConfig(
        SecurityProfile security,
        ResourceLimits resources,
        NetworkPolicy networkPolicy
    ) {
        // Enforcement floors: these security invariants are always applied regardless
        // of the caller-supplied SecurityProfile. A compromised or misconfigured caller
        // cannot weaken the sandbox below these minimums.

        // Security options — no-new-privileges is always enforced
        List<String> securityOpts = new ArrayList<>();
        securityOpts.add("no-new-privileges");

        // Apply cached seccomp profile
        if (seccompProfileJson != null) {
            securityOpts.add("seccomp=" + seccompProfileJson);
        }

        // DNS: block all DNS when internet is disabled (prevents DNS exfiltration)
        List<String> dns = new ArrayList<>();
        if (networkPolicy == null || !networkPolicy.internetAccess()) {
            dns.add("0.0.0.0");
        }

        // Ulimits: nofile, nproc (defense-in-depth alongside pidsLimit), core=0 (no core dumps)
        Map<String, DockerOperations.UlimitSpec> ulimits = Map.of(
            "nofile",
            new DockerOperations.UlimitSpec(NOFILE_LIMIT, NOFILE_LIMIT),
            "nproc",
            new DockerOperations.UlimitSpec(resources.pidsLimit(), resources.pidsLimit()),
            "core",
            new DockerOperations.UlimitSpec(0, 0)
        );

        // Resolve runtime: global config is the enforcement floor — callers cannot downgrade.
        // If global says "runsc", caller cannot set "runc" to bypass gVisor.
        String globalRuntime = properties.containerRuntime();
        String runtime;
        if (globalRuntime != null && !globalRuntime.isBlank()) {
            // Global runtime is the floor — always use it (callers cannot downgrade)
            runtime = globalRuntime;
        } else {
            runtime = security.runtime();
        }

        // Enforce minimum capability dropping: caller-supplied list must include "ALL".
        // If not, override with the safe default.
        List<String> dropCaps = security.dropCapabilities();
        if (dropCaps == null || !dropCaps.contains("ALL")) {
            dropCaps = List.of("ALL");
        }

        // Enforce IPC mode floor: only "none" and "private" are allowed.
        // "host" or "shareable" would leak host shared memory — always rejected.
        String ipcMode = security.ipcMode();
        if (ipcMode == null || !ALLOWED_IPC_MODES.contains(ipcMode)) {
            ipcMode = "none";
        }

        // Enforce mandatory tmpfs mounts with noexec. Caller-supplied mounts are merged,
        // but mandatory paths always keep their hardened options.
        Map<String, String> tmpfs = new HashMap<>(MANDATORY_TMPFS);
        if (security.tmpfsMounts() != null) {
            for (var entry : security.tmpfsMounts().entrySet()) {
                // Only add caller mounts for paths NOT in mandatory set
                if (!MANDATORY_TMPFS.containsKey(entry.getKey())) {
                    tmpfs.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return new DockerOperations.HostConfigSpec(
            resources.memoryBytes(),
            resources.memoryBytes(), // memory-swap = memory (no swap)
            (long) (resources.cpus() * NANO_CPUS_PER_CPU), // nanoCPUs
            resources.pidsLimit(),
            false, // read-only rootfs disabled: docker cp injects files before start (before tmpfs mounts exist)
            false, // never privileged
            dropCaps,
            securityOpts,
            tmpfs,
            dns,
            "private", // cgroup namespace always private
            ipcMode,
            runtime,
            ulimits
        );
    }

    /** Build container labels for lifecycle management and reconciliation. */
    public Map<String, String> buildLabels(UUID jobId) {
        return Map.of(SandboxLabels.MANAGED, "true", SandboxLabels.JOB_ID, jobId.toString());
    }
}
