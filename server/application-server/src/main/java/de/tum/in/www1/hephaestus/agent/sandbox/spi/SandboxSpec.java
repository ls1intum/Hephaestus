package de.tum.in.www1.hephaestus.agent.sandbox.spi;

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
 * <p>Files are injected via {@code docker cp} (tar archive API), not bind mounts. This works
 * identically for local and remote Docker daemons, enabling future multi-node runner support
 * without any change to this contract.
 *
 * @param jobId unique job identifier (used for labels, network naming, logging)
 * @param image Docker image to run (e.g. {@code ghcr.io/ls1intum/hephaestus/agent-opencode:latest})
 * @param command container command + arguments
 * @param environment environment variables injected into the container
 * @param networkPolicy network access and LLM proxy configuration
 * @param resourceLimits CPU, memory, PID, and timeout constraints
 * @param securityProfile container hardening flags
 * @param inputFiles files to inject into /workspace via docker cp (relative path → content)
 * @param outputPath container path to collect results from after execution
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
    String outputPath) {
  public SandboxSpec {
    Objects.requireNonNull(jobId, "jobId must not be null");
    Objects.requireNonNull(image, "image must not be null");
    Objects.requireNonNull(resourceLimits, "resourceLimits must not be null");
    if (image.isBlank()) {
      throw new IllegalArgumentException("image must not be blank");
    }
  }
}
