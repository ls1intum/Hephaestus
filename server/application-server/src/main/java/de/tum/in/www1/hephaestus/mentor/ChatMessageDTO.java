package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * AI SDK UIMessage shape served to the webapp. Parts are the JSONB array (or the legacy
 * fallback reconstruction); metadata is the per-turn {status, model, costUsd, …} object.
 * {@code parts} and {@code metadata} are open JSON ({@link JsonNode} on the wire,
 * {@code Object} in the OpenAPI spec) to keep the AI SDK chunk schema authoritative.
 */
@Schema(description = "Mentor chat message in AI SDK UIMessage shape.")
public record ChatMessageDTO(
    UUID id,
    @Nullable UUID parentMessageId,
    String role,
    @Schema(description = "AI SDK UIMessage parts array.", type = "array") Object parts,
    @Nullable @Schema(description = "Per-turn metadata: status, model, costUsd, usage, …") Object metadata,
    Instant createdAt
) {
    public static ChatMessageDTO from(ChatMessage message, JsonNode effectiveParts) {
        return new ChatMessageDTO(
            message.getId(),
            message.getParentMessage() != null ? message.getParentMessage().getId() : null,
            message.getRole().getValue(),
            effectiveParts,
            message.getMetadata(),
            message.getCreatedAt()
        );
    }
}
