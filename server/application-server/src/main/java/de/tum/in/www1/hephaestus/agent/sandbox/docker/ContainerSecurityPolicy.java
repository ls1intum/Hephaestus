package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Translates {@link SecurityProfile} and {@link ResourceLimits} into a {@link
 * DockerOperations.HostConfigSpec} for container creation.
 *
 * <p>Applies defense-in-depth hardening:
 *
 * <ul>
 *   <li>{@code --cap-drop=ALL} — drop all Linux capabilities
 *   <li>{@code --security-opt=no-new-privileges} — prevent setuid/setgid escalation
 *   <li>{@code --read-only} — immutable root filesystem
 *   <li>{@code --user 1000:1000} — non-root execution
 *   <li>{@code --cgroupns=private} — prevent cgroup hierarchy leaks
 *   <li>{@code --ipc=none} — close IPC vectors
 *   <li>{@code --dns 0.0.0.0} — block DNS (prevents exfiltration, CVE-2024-29018)
 *   <li>tmpfs with {@code noexec} on /tmp and /run
 *   <li>Custom seccomp profile (loaded once at startup)
 *   <li>Optional gVisor runtime ({@code --runtime=runsc})
 * </ul>
 */
public class ContainerSecurityPolicy {

    private static final int NOFILE_LIMIT = 1024;
    private static final long NANO_CPUS_PER_CPU = 1_000_000_000L;

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

        // Ulimits
        Map<String, DockerOperations.UlimitSpec> ulimits = Map.of(
            "nofile",
            new DockerOperations.UlimitSpec(NOFILE_LIMIT, NOFILE_LIMIT)
        );

        // Resolve runtime (from security profile or global config)
        String runtime = security.runtime();
        if (runtime == null && properties.containerRuntime() != null) {
            runtime = properties.containerRuntime();
        }

        // Enforce minimum capability dropping: caller-supplied list must include "ALL".
        // If not, override with the safe default.
        List<String> dropCaps = security.dropCapabilities();
        if (dropCaps == null || !dropCaps.contains("ALL")) {
            dropCaps = List.of("ALL");
        }

        return new DockerOperations.HostConfigSpec(
            resources.memoryBytes(),
            resources.memoryBytes(), // memory-swap = memory (no swap)
            (long) (resources.cpus() * NANO_CPUS_PER_CPU), // nanoCPUs
            resources.pidsLimit(),
            true, // read-only rootfs always enforced
            false, // never privileged
            dropCaps,
            securityOpts,
            security.tmpfsMounts() != null ? new HashMap<>(security.tmpfsMounts()) : Map.of(),
            dns,
            "private", // cgroup namespace always private
            security.ipcMode() != null ? security.ipcMode() : "none",
            runtime,
            ulimits
        );
    }

    /** Build container labels for lifecycle management and reconciliation. */
    public Map<String, String> buildLabels(UUID jobId) {
        return Map.of(SandboxLabels.MANAGED, "true", SandboxLabels.JOB_ID, jobId.toString());
    }
}
