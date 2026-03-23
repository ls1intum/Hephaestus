package de.tum.in.www1.hephaestus.agent.adapter.spi;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Agent-specific sandbox configuration produced by an {@link AgentAdapter}.
 *
 * <p>The orchestrator combines this with job-level concerns ({@code jobId},
 * {@link de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits}) to build the final
 * {@link de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec}.
 *
 * @param image           Docker image to run
 * @param command         container command + arguments (Docker exec form)
 * @param environment     agent-specific environment variables
 * @param inputFiles      files to inject into /workspace (relative path → content)
 * @param outputPath      container path to collect results from
 * @param securityProfile security hardening (null = use SecurityProfile.DEFAULT)
 * @param networkPolicy   network access and LLM proxy configuration
 * @param volumeMounts    host bind mounts (host path → container path); mounted read-only
 */
public record AgentSandboxSpec(
    String image,
    List<String> command,
    Map<String, String> environment,
    Map<String, byte[]> inputFiles,
    String outputPath,
    @Nullable SecurityProfile securityProfile,
    @Nullable NetworkPolicy networkPolicy,
    Map<String, String> volumeMounts
) {
    public AgentSandboxSpec {
        Objects.requireNonNull(image, "image must not be null");
        if (image.isBlank()) {
            throw new IllegalArgumentException("image must not be blank");
        }
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        if (outputPath.isBlank()) {
            throw new IllegalArgumentException("outputPath must not be blank");
        }
        command = command != null ? List.copyOf(command) : List.of();
        environment = environment != null ? Map.copyOf(environment) : Map.of();
        inputFiles = inputFiles != null ? Map.copyOf(inputFiles) : Map.of();
        volumeMounts = volumeMounts != null ? Map.copyOf(volumeMounts) : Map.of();
    }
}
