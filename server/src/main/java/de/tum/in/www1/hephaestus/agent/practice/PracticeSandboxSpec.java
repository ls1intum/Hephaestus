package de.tum.in.www1.hephaestus.agent.practice;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Sandbox configuration produced by {@link PracticePiAdapter#buildSandboxSpec}.
 * The executor combines it with job-level concerns into a {@link
 * de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec}. {@code inputFiles} paths are
 * workspace-relative; {@code volumeMounts} are bind-mounted read-only.
 */
public record PracticeSandboxSpec(
    String image,
    List<String> command,
    Map<String, String> environment,
    Map<String, byte[]> inputFiles,
    String outputPath,
    @Nullable SecurityProfile securityProfile,
    @Nullable NetworkPolicy networkPolicy,
    Map<String, String> volumeMounts
) {
    public PracticeSandboxSpec {
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
