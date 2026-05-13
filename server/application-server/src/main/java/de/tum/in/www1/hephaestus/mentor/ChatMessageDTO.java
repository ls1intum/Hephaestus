package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * AI SDK UIMessage shape served to the webapp. Parts are the JSONB array (or the legacy
 * fallback reconstruction); metadata is the per-turn {status, model, costUsd, …} object.
 */
@Schema(description = "Mentor chat message in AI SDK UIMessage shape.")
public record ChatMessageDTO(
    UUID id,
    @Nullable UUID parentMessageId,
    String role,
    JsonNode parts,
    @Nullable JsonNode metadata,
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
