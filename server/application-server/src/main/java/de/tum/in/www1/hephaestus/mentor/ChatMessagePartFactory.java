package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Factory class for creating strongly-typed message parts matching the AI SDK UI message schema.
 * Provides methods for creating each message part type with proper typing.
 */
public final class ChatMessagePartFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ChatMessagePartFactory() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a text part with the specified text content
     */
    public static ChatMessagePart createTextPart(UUID messageId, int orderIndex, String text) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.TEXT);
        part.setOriginalType("text");

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "text");
        content.put("text", text);
        part.setContent(content);

        return part;
    }

    /**
     * Creates a reasoning part with the specified text content and optional provider metadata
     */
    public static ChatMessagePart createReasoningPart(
        UUID messageId,
        int orderIndex,
        String text,
        @Nullable Map<String, Object> providerMetadata
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.REASONING);
        part.setOriginalType("reasoning");

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "reasoning");
        content.put("text", text);

        if (providerMetadata != null) {
            content.set("providerMetadata", OBJECT_MAPPER.valueToTree(providerMetadata));
        }

        part.setContent(content);
        return part;
    }

    /**
     * Creates a tool call part in "call" state with specified tool name, ID and args
     */
    public static ChatMessagePart createToolCallPart(
        UUID messageId,
        int orderIndex,
        String toolName,
        String toolCallId,
        Object args
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.TOOL);
        part.setOriginalType("tool-" + toolName);

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "tool-" + toolName);
        content.put("toolCallId", toolCallId);
        content.put("state", "call");
        content.set("args", OBJECT_MAPPER.valueToTree(args));

        part.setContent(content);
        return part;
    }

    /**
     * Creates a tool call part in "partial-call" state with specified tool name, ID and partial args
     */
    public static ChatMessagePart createPartialToolCallPart(
        UUID messageId,
        int orderIndex,
        String toolName,
        String toolCallId,
        Object partialArgs
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.TOOL);
        part.setOriginalType("tool-" + toolName);

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "tool-" + toolName);
        content.put("toolCallId", toolCallId);
        content.put("state", "partial-call");
        content.set("args", OBJECT_MAPPER.valueToTree(partialArgs));

        part.setContent(content);
        return part;
    }

    /**
     * Creates a tool result part with specified tool name, ID, args and result
     */
    public static ChatMessagePart createToolResultPart(
        UUID messageId,
        int orderIndex,
        String toolName,
        String toolCallId,
        Object args,
        Object result
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.TOOL);
        part.setOriginalType("tool-" + toolName);

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "tool-" + toolName);
        content.put("toolCallId", toolCallId);
        content.put("state", "result");
        content.set("args", OBJECT_MAPPER.valueToTree(args));
        content.set("result", OBJECT_MAPPER.valueToTree(result));

        part.setContent(content);
        return part;
    }

    /**
     * Creates a source URL part
     */
    public static ChatMessagePart createSourceUrlPart(
        UUID messageId,
        int orderIndex,
        String sourceId,
        String url,
        @Nullable String title,
        @Nullable Map<String, Object> providerMetadata
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.SOURCE_URL);
        part.setOriginalType("source-url");

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "source-url");
        content.put("sourceId", sourceId);
        content.put("url", url);

        if (title != null) {
            content.put("title", title);
        }

        if (providerMetadata != null) {
            content.set("providerMetadata", OBJECT_MAPPER.valueToTree(providerMetadata));
        }

        part.setContent(content);
        return part;
    }

    /**
     * Creates a source document part
     */
    public static ChatMessagePart createSourceDocumentPart(
        UUID messageId,
        int orderIndex,
        String sourceId,
        String mediaType,
        String title,
        @Nullable String filename,
        @Nullable Map<String, Object> providerMetadata
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.SOURCE_DOCUMENT);
        part.setOriginalType("source-document");

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "source-document");
        content.put("sourceId", sourceId);
        content.put("mediaType", mediaType);
        content.put("title", title);

        if (filename != null) {
            content.put("filename", filename);
        }

        if (providerMetadata != null) {
            content.set("providerMetadata", OBJECT_MAPPER.valueToTree(providerMetadata));
        }

        part.setContent(content);
        return part;
    }

    /**
     * Creates a file part
     */
    public static ChatMessagePart createFilePart(
        UUID messageId,
        int orderIndex,
        String mediaType,
        String url,
        @Nullable String filename
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.FILE);
        part.setOriginalType("file");

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "file");
        content.put("mediaType", mediaType);
        content.put("url", url);

        if (filename != null) {
            content.put("filename", filename);
        }

        part.setContent(content);
        return part;
    }

    /**
     * Creates a data part with the specified type
     */
    public static ChatMessagePart createDataPart(
        UUID messageId,
        int orderIndex,
        String dataType,
        Object data,
        @Nullable String id
    ) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.DATA);
        part.setOriginalType("data-" + dataType);

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "data-" + dataType);
        content.set("data", OBJECT_MAPPER.valueToTree(data));

        if (id != null) {
            content.put("id", id);
        }

        part.setContent(content);
        return part;
    }

    /**
     * Creates a step start part
     */
    public static ChatMessagePart createStepStartPart(UUID messageId, int orderIndex) {
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.STEP_START);
        part.setOriginalType("step-start");

        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        content.put("type", "step-start");

        part.setContent(content);
        return part;
    }

    /**
     * Creates a message part from a raw UI message part object
     * Preserves the exact structure while mapping to our type system
     */
    public static ChatMessagePart fromUIMessagePart(UUID messageId, int orderIndex, JsonNode uiPart) {
        if (!uiPart.has("type")) {
            throw new IllegalArgumentException("UI message part must have a type");
        }

        String typeString = uiPart.get("type").asText();
        ChatMessagePart part = new ChatMessagePart();
        part.setId(new ChatMessagePartId(messageId, orderIndex));
        part.setType(ChatMessagePart.PartType.fromValue(typeString));
        part.setOriginalType(typeString);
        part.setContent(uiPart);

        return part;
    }
}
