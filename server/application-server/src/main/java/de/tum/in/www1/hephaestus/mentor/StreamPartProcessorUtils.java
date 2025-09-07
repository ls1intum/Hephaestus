package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for common stream part processing logic.
 */
public class StreamPartProcessorUtils {

    private static final Logger logger = LoggerFactory.getLogger(StreamPartProcessorUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JsonNullableModule());

    public static final String DONE_MARKER = "[DONE]";

    /**
     * Process a JSON chunk and trigger appropriate callbacks.
     */
    public static void processStreamChunk(String jsonChunk, StreamPartProcessor processor) {
        if (jsonChunk == null || jsonChunk.trim().isEmpty() || DONE_MARKER.equals(jsonChunk.trim())) {
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(jsonChunk);
            JsonNode typeNode = jsonNode.get("type");

            if (typeNode == null || !typeNode.isTextual()) {
                return;
            }

            String type = typeNode.asText();
            Object streamPart = parseStreamPartByType(type, jsonNode);

            if (streamPart != null) {
                callProcessorCallback(streamPart, processor);
            }
        } catch (Exception e) {
            logger.error("Failed to parse stream chunk: {}", jsonChunk, e);
        }
    }

    private static Object parseStreamPartByType(String type, JsonNode jsonNode) {
        Class<?> clazz = null;
        switch (type) {
            case "start" -> clazz = StreamStartPart.class;
            case "text-start" -> clazz = StreamTextStartPart.class;
            case "text-delta" -> clazz = StreamTextDeltaPart.class;
            case "text-end" -> clazz = StreamTextEndPart.class;
            case "finish" -> clazz = StreamFinishPart.class;
            case "error" -> clazz = StreamErrorPart.class;
            case "tool-input-start" -> clazz = StreamToolInputStartPart.class;
            case "tool-input-delta" -> clazz = StreamToolInputDeltaPart.class;
            case "tool-input-available" -> clazz = StreamToolInputAvailablePart.class;
            case "tool-input-error" -> clazz = StreamToolInputErrorPart.class;
            case "tool-output-available" -> clazz = StreamToolOutputAvailablePart.class;
            case "tool-output-error" -> clazz = StreamToolOutputErrorPart.class;
            case "reasoning-start" -> clazz = StreamReasoningStartPart.class;
            case "reasoning-delta" -> clazz = StreamReasoningDeltaPart.class;
            case "reasoning-end" -> clazz = StreamReasoningEndPart.class;
            case "source-url" -> clazz = StreamSourceUrlPart.class;
            case "source-document" -> clazz = StreamSourceDocumentPart.class;
            case "file" -> clazz = StreamFilePart.class;
            case "start-step" -> clazz = StreamStepStartPart.class;
            case "finish-step" -> clazz = StreamStepFinishPart.class;
            case "message-metadata" -> clazz = StreamMessageMetadataPart.class;
            default -> {
                if (type.startsWith("data-")) {
                    clazz = StreamDataPart.class;
                } else {
                    logger.warn("Unknown stream part type: {}", type);
                }
            }
        }
        if (clazz != null) {
            return parseStreamPart(jsonNode, clazz);
        }
        return null;
    }

    private static <T> T parseStreamPart(JsonNode jsonNode, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(jsonNode, clazz);
        } catch (Exception e) {
            logger.error("Failed to parse stream part of type {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Call appropriate processor callback based on stream part type.
     */
    public static void callProcessorCallback(Object streamPart, StreamPartProcessor processor) {
        try {
            switch (streamPart) {
                case StreamStartPart start -> processor.onStreamStart(start);
                case StreamTextStartPart textStart -> processor.onTextStart(textStart);
                case StreamTextDeltaPart textDelta -> processor.onTextDelta(textDelta);
                case StreamTextEndPart textEnd -> processor.onTextEnd(textEnd);
                case StreamErrorPart error -> processor.onStreamError(error);
                case StreamFinishPart finish -> processor.onStreamFinish(finish);
                case StreamToolInputStartPart toolStart -> processor.onToolInputStart(toolStart);
                case StreamToolInputDeltaPart toolDelta -> processor.onToolInputDelta(toolDelta);
                case StreamToolInputAvailablePart toolInput -> processor.onToolInputAvailable(toolInput);
                case StreamToolOutputAvailablePart toolOutput -> processor.onToolOutputAvailable(toolOutput);
                case StreamToolInputErrorPart toolInputError -> processor.onToolInputError(toolInputError);
                case StreamToolOutputErrorPart errorPart -> processor.onToolOutputError(errorPart);
                case StreamReasoningStartPart reasoningStart -> processor.onReasoningStart(reasoningStart);
                case StreamReasoningDeltaPart reasoningDelta -> processor.onReasoningDelta(reasoningDelta);
                case StreamReasoningEndPart reasoningEnd -> processor.onReasoningEnd(reasoningEnd);
                case StreamSourceUrlPart sourceUrl -> processor.onSourceUrl(sourceUrl);
                case StreamSourceDocumentPart sourceDocument -> processor.onSourceDocument(sourceDocument);
                case StreamFilePart file -> processor.onFile(file);
                case StreamDataPart data -> processor.onDataPart(data);
                case StreamStepStartPart stepStart -> processor.onStepStart(stepStart);
                case StreamStepFinishPart stepFinish -> processor.onStepFinish(stepFinish);
                case StreamMessageMetadataPart messageMetadata -> processor.onMessageMetadata(messageMetadata);
                default -> processor.onUnknownStreamPart(streamPart);
            }
        } catch (Exception e) {
            logger.error(
                "Error in stream part processor callback for {}: {}",
                streamPart.getClass().getSimpleName(),
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Converts an object to a JSON string formatted for Server-Sent Events (SSE).
     *
     * @param data The data to convert
     * @return The SSE formatted string
     */
    public static String streamPartToJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize stream part", e);
        }
    }
}
