package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete specification for a sandboxed container execution.
 *
 * <p>Domain-agnostic — the sandbox manager never interprets the contents of {@code inputFiles} or
 * the data written to {@code outputPath}. Handlers and adapters produce a {@code SandboxSpec}; the
 * sandbox manager executes it.
 *
 * <p>Files are injected into the container workspace before execution and collected from the
 * output path after completion. The transfer mechanism is an implementation detail of the
 * underlying {@link SandboxManager}.
 *
 * @param jobId unique job identifier (used for labels, network naming, logging)
 * @param image Docker image to run (e.g. {@code ghcr.io/ls1intum/hephaestus/agent-pi:latest})
 * @param command container command + arguments
 * @param environment environment variables injected into the container
 * @param networkPolicy network access and LLM proxy configuration
 * @param resourceLimits CPU, memory, PID, and timeout constraints
 * @param securityProfile container hardening flags
 * @param inputFiles files to inject into /workspace (relative path → content)
 * @param outputPath container path to collect results from after execution
 * @param volumeMounts host bind mounts (host path → container path); mounted read-only
 * @param symlinks workspace-relative symlinks to create after injection (link → relative target),
 *     e.g. the back-compat {@code repo} → {@code blobs/scm/repo} alias
 */
public record SandboxSpec(
    UUID jobId,
    String image,
    List<String> command,
    Map<String, String> environment,
    NetworkPolicy networkPolicy,
    ResourceLimits resourceLimits,
    SecurityProfile securityProfile,
    Map<String, byte[]> inputFiles,
    String outputPath,
    Map<String, String> volumeMounts,
    Map<String, String> symlinks
) {
    public SandboxSpec {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(image, "image must not be null");
        Objects.requireNonNull(resourceLimits, "resourceLimits must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        if (image.isBlank()) {
            throw new IllegalArgumentException("image must not be blank");
        }
        if (outputPath.isBlank()) {
            throw new IllegalArgumentException("outputPath must not be blank");
        }
        // Default nullable collection fields to empty — avoids null-checking in consumers
        command = command != null ? command : List.of();
        environment = environment != null ? environment : Map.of();
        inputFiles = inputFiles != null ? inputFiles : Map.of();
        volumeMounts = volumeMounts != null ? volumeMounts : Map.of();
        symlinks = symlinks != null ? symlinks : Map.of();
    }

    /** Convenience overload for the common case of a spec with no symlinks (defaults them to empty). */
    public SandboxSpec(
        UUID jobId,
        String image,
        List<String> command,
        Map<String, String> environment,
        NetworkPolicy networkPolicy,
        ResourceLimits resourceLimits,
        SecurityProfile securityProfile,
        Map<String, byte[]> inputFiles,
        String outputPath,
        Map<String, String> volumeMounts
    ) {
        this(
            jobId,
            image,
            command,
            environment,
            networkPolicy,
            resourceLimits,
            securityProfile,
            inputFiles,
            outputPath,
            volumeMounts,
            Map.of()
        );
    }
}
