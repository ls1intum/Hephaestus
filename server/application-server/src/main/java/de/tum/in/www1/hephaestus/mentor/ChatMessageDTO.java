package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * AI SDK UIMessage shape served to the webapp. Parts are the JSONB array (or the legacy
 * fallback reconstruction); metadata is the per-turn {status, model, costUsd, …} object.
 *
 * <p>Field types are {@code List<Object>} / {@code Map<String, Object>} so springdoc emits
 * proper {@code type: array} / {@code type: object} schemas in OpenAPI — declaring them as
 * {@code Object} downgrades to {@code type: string}, and declaring them as {@code JsonNode}
 * emits an unresolved {@code $ref}. The {@link ChatMessageDTO#from} factory converts the
 * authoritative {@link JsonNode} representation to the OpenAPI-friendly shape; the AI SDK
 * {@code UIMessageChunk} schema is still the source of truth for chunk validation client-side.
 */
@Schema(description = "Mentor chat message in AI SDK UIMessage shape.")
public record ChatMessageDTO(
    UUID id,
    @Nullable UUID parentMessageId,
    String role,
    @ArraySchema(
        schema = @Schema(
            type = "object",
            description = "AI SDK UIMessage part (text / reasoning / tool / data-finding)."
        )
    )
    List<Object> parts,
    @Nullable
    @Schema(description = "Per-turn metadata: status, model, costUsd, usage, …", type = "object")
    Map<String, Object> metadata,
    Instant createdAt
) {
    /**
     * Build a DTO from the entity + the effective parts {@link JsonNode}. Conversion is via
     * the supplied {@code ObjectMapper} so we honour the application's Jackson config (e.g.
     * Instant serialisation, JavaTimeModule). The shape on the wire is a JSON array of
     * objects for {@code parts} and a JSON object for {@code metadata}, both opaque from
     * Java's perspective.
     */
    @SuppressWarnings("unchecked")
    public static ChatMessageDTO from(ChatMessage message, JsonNode effectiveParts, ObjectMapper mapper) {
        List<Object> parts =
            effectiveParts != null && effectiveParts.isArray()
                ? mapper.convertValue(effectiveParts, new TypeReference<List<Object>>() {})
                : List.of();
        Map<String, Object> metadata =
            message.getMetadata() != null && message.getMetadata().isObject()
                ? mapper.convertValue(message.getMetadata(), new TypeReference<Map<String, Object>>() {})
                : null;
        return new ChatMessageDTO(
            message.getId(),
            message.getParentMessage() != null ? message.getParentMessage().getId() : null,
            message.getRole().getValue(),
            parts,
            metadata,
            message.getCreatedAt()
        );
    }
}
