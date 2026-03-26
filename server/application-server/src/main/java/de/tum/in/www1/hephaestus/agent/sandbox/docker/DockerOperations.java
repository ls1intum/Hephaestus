package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import java.util.List;
import java.util.Map;

/**
 * Shared value types for Docker sandbox operations.
 *
 * <p>These records are used by the focused operation interfaces ({@link DockerContainerOperations},
 * {@link DockerNetworkOperations}, {@link DockerFileOperations}) and their implementations.
 */
final class DockerOperations {

    private DockerOperations() {}

    /** Specification for creating a container. */
    record ContainerSpec(
        String image,
        List<String> command,
        Map<String, String> environment,
        String networkId,
        String hostname,
        String user,
        Map<String, String> labels,
        HostConfigSpec hostConfig,
        List<String> extraHosts
    ) {}

    /** Host configuration for container resource limits and security. */
    record HostConfigSpec(
        long memoryBytes,
        long memorySwapBytes,
        long nanoCpus,
        int pidsLimit,
        boolean readonlyRootfs,
        boolean privileged,
        List<String> capDrop,
        List<String> securityOpts,
        Map<String, String> tmpfsMounts,
        List<String> dns,
        String cgroupnsMode,
        String ipcMode,
        String runtime,
        Map<String, UlimitSpec> ulimits
    ) {}

    record UlimitSpec(long soft, long hard) {}

    record WaitResult(int exitCode) {}

    record ContainerInfo(String id, String name, Map<String, String> labels, String state) {}

    record NetworkInfo(String id, String name) {}
}
