package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Complete specification for an interactive (long-lived attached) sandbox.
 *
 * <p>Mirrors {@link SandboxSpec} but for streamable JSONL IO over {@code docker exec -i}. The
 * implementation creates a sleeper container ({@code tail -f /dev/null} as CMD), injects {@code
 * inputFiles} / {@code volumeMounts} via the same tar pipeline as the sync sandbox, then execs the
 * runner inside the container.
 *
 * <p><strong>Registry key.</strong> {@code (userId, workspaceId)} uniquely identifies a session.
 * Concurrent {@code attach()} calls with the same key share the returned {@code AttachedSandbox}
 * instance — this matches the user-facing model of "one mentor session per workspace at a time".
 *
 * @param sessionId unique session identifier (used for container labels, network naming, logging)
 * @param userId opaque user identifier; one half of the registry key
 * @param workspaceId opaque workspace identifier; second half of the registry key
 * @param image Docker image (e.g. {@code ghcr.io/ls1intum/hephaestus/agent-pi:latest})
 * @param command runner command + args (e.g. {@code ["node", "/workspace/.runner/pi-mentor-runner.mjs"]})
 * @param environment env vars passed to the runner via {@code docker exec}
 * @param networkPolicy network access and LLM proxy configuration
 * @param resourceLimits CPU, memory, PID constraints (maxRuntime is the container hard-cap, not the
 *     session idle TTL — idle TTL is configured globally via {@code hephaestus.mentor.idle-ttl-seconds})
 * @param securityProfile container hardening flags
 * @param inputFiles files injected into {@code /workspace} before container start (relative path →
 *     content); chowned to {@code 1000:1000} by the tar pipeline
 * @param volumeMounts host directories injected via tar (host path → container path)
 */
public record InteractiveSandboxSpec(
    UUID sessionId,
    String userId,
    String workspaceId,
    String image,
    List<String> command,
    Map<String, String> environment,
    NetworkPolicy networkPolicy,
    ResourceLimits resourceLimits,
    SecurityProfile securityProfile,
    Map<String, byte[]> inputFiles,
    Map<String, String> volumeMounts
) {
    /** POSIX-shell env-var name shape: leading letter/underscore, then letters/digits/underscores. */
    private static final Pattern ENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    public InteractiveSandboxSpec {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(image, "image must not be null");
        Objects.requireNonNull(resourceLimits, "resourceLimits must not be null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (image.isBlank()) {
            throw new IllegalArgumentException("image must not be blank");
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty — runner needs an entry point");
        }
        environment = environment != null ? environment : Map.of();
        inputFiles = inputFiles != null ? inputFiles : Map.of();
        volumeMounts = volumeMounts != null ? volumeMounts : Map.of();
        for (var entry : environment.entrySet()) {
            String key = entry.getKey();
            if (key == null || !ENV_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                    "Invalid env var name (must match " + ENV_KEY.pattern() + "): " + key
                );
            }
            String value = entry.getValue();
            if (value != null && value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Env var value contains NUL: " + key);
            }
        }
    }
}
