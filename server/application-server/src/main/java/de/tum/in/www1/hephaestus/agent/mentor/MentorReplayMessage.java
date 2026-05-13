package de.tum.in.www1.hephaestus.agent.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/**
 * One AI SDK UIMessage entry passed to the mentor runner as replay context.
 *
 * @param role       {@code "user"} or {@code "assistant"}; other roles are dropped by the runner
 * @param parts      AI SDK UIMessage parts as a JSON array (must be array)
 * @param createdAt  message timestamp for chronological ordering
 */
public record MentorReplayMessage(String role, JsonNode parts, Instant createdAt) {
    public MentorReplayMessage {
        Objects.requireNonNull(role, "role");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        Objects.requireNonNull(parts, "parts");
        if (!parts.isArray()) {
            throw new IllegalArgumentException("parts must be a JSON array, got " + parts.getNodeType());
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
