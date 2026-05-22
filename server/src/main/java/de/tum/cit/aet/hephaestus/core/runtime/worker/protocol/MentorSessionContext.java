package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Payload carried inside {@link SessionOpen#context()} for {@link SessionKind#MENTOR_INTERACTIVE}.
 * The hub provides per-turn fields (user, workspace, image, command, env); the worker fills in
 * {@code SecurityProfile} / {@code NetworkPolicy} from its own defaults — the hub is not trusted
 * to override security.
 *
 * <p>Env values must not contain NUL/LF/CR (enforced by {@code InteractiveSandboxSpec}).
 */
public record MentorSessionContext(
    String userId,
    String workspaceId,
    String image,
    List<String> command,
    Map<String, String> environment,
    @Nullable Limits limits
) {
    public MentorSessionContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(command, "command");
        if (userId.isBlank() || workspaceId.isBlank() || image.isBlank()) {
            throw new IllegalArgumentException("MentorSessionContext: userId/workspaceId/image must not be blank");
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("MentorSessionContext.command must not be empty");
        }
        command = List.copyOf(command);
        environment = environment == null ? Collections.emptyMap() : Map.copyOf(environment);
    }

    public record Limits(@Nullable Long memoryBytes, @Nullable Double cpus, @Nullable Integer pidsLimit) {}
}
