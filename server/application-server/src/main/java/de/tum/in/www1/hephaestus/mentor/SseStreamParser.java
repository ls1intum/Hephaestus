package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.intelligenceservice.model.*;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Utility class for parsing Server-Sent Events (SSE) from the intelligence service.
 * Handles the new JSON-based streaming protocol.
 */
public class SseStreamParser {
    
    private static final Logger logger = LoggerFactory.getLogger(SseStreamParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JsonNullableModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";
    
    /**
     * Parses an SSE line and returns the appropriate stream part object.
     * 
     * @param line The SSE line to parse
     * @return Optional containing the parsed stream part, or empty if invalid/done
     */
    public static Optional<Object> parseSSELine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String trimmed = line.trim();
        
        // Handle SSE comments and empty lines
        if (trimmed.startsWith(":") || trimmed.isEmpty()) {
            return Optional.empty();
        }
        
        // Handle data lines
        if (trimmed.startsWith(DATA_PREFIX)) {
            String jsonData = trimmed.substring(DATA_PREFIX.length()).trim();
            
            // Handle DONE marker
            if (DONE_MARKER.equals(jsonData)) {
                logger.debug("Received DONE marker, ending stream");
                return Optional.empty();
            }
            
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonData);
                JsonNode typeNode = jsonNode.get("type");
                
                if (typeNode == null || !typeNode.isTextual()) {
                    logger.warn("Missing or invalid 'type' field in SSE data: {}", jsonData);
                    return Optional.empty();
                }
                
                String type = typeNode.asText();
                
                // Handle fixed types first
                return switch (type) {
                    case "start" -> parseStreamPart(jsonNode, StreamStartPart.class);
                    case "text" -> parseStreamPart(jsonNode, StreamTextPart.class);
                    case "error" -> parseStreamPart(jsonNode, StreamErrorPart.class);
                    case "finish" -> parseStreamPart(jsonNode, StreamFinishPart.class);
                    case "start-step" -> parseStreamPart(jsonNode, StreamStepStartPart.class);
                    case "finish-step" -> parseStreamPart(jsonNode, StreamStepFinishPart.class);
                    case "tool-input-start" -> parseStreamPart(jsonNode, StreamToolInputStartPart.class);
                    case "tool-input-delta" -> parseStreamPart(jsonNode, StreamToolInputDeltaPart.class);
                    case "tool-input-available" -> parseStreamPart(jsonNode, StreamToolInputAvailablePart.class);
                    case "tool-output-available" -> parseStreamPart(jsonNode, StreamToolOutputAvailablePart.class);
                    case "reasoning" -> parseStreamPart(jsonNode, StreamReasoningPart.class);
                    case "reasoning-part-finish" -> parseStreamPart(jsonNode, StreamReasoningFinishPart.class); // Correct Python type
                    case "message-metadata" -> parseStreamPart(jsonNode, StreamMessageMetadataPart.class);
                    case "file" -> parseStreamPart(jsonNode, StreamFilePart.class);
                    case "source-document" -> parseStreamPart(jsonNode, StreamSourceDocumentPart.class);
                    case "source-url" -> parseStreamPart(jsonNode, StreamSourceUrlPart.class);
                    default -> {
                        // Handle dynamic types with patterns
                        if (type.startsWith("data-")) {
                            // Handle data-{NAME} pattern (e.g., data-weather, data-location)
                            yield parseStreamPart(jsonNode, StreamDataPart.class);
                        } else {
                            logger.warn("Unknown stream part type: {}", type);
                            yield Optional.empty();
                        }
                    }
                };
                
            } catch (Exception e) {
                logger.error("Failed to parse SSE data: {}", jsonData, e);
                return Optional.empty();
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Helper method to parse a stream part with better error handling.
     * 
     * @param jsonNode The JSON node to parse
     * @param clazz The target class
     * @return Optional containing the parsed object or empty if parsing failed
     */
    private static <T> Optional<Object> parseStreamPart(JsonNode jsonNode, Class<T> clazz) {
        try {
            T result = objectMapper.treeToValue(jsonNode, clazz);
            return Optional.of(result);
        } catch (Exception e) {
            logger.error("Failed to parse {} from JSON: {}", clazz.getSimpleName(), jsonNode.toString(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Converts a stream part object to its SSE representation.
     * 
     * @param streamPart The stream part object to convert
     * @return SSE formatted string
     */
    public static String toSSEFormat(Object streamPart) {
        try {
            String json = objectMapper.writeValueAsString(streamPart);
            return DATA_PREFIX + json + "\n\n";
        } catch (Exception e) {
            logger.error("Failed to serialize stream part to SSE format", e);
            return "";
        }
    }
    
    /**
     * Creates an SSE formatted error response.
     * 
     * @param errorMessage The error message
     * @return SSE formatted error string
     */
    public static String createErrorSSE(String errorMessage) {
        StreamErrorPart errorPart = new StreamErrorPart();
        errorPart.setErrorText(errorMessage);
        return toSSEFormat(errorPart);
    }
    
    /**
     * Creates an SSE formatted finish response.
     * 
     * @return SSE formatted finish string
     */
    public static String createFinishSSE() {
        StreamFinishPart finishPart = new StreamFinishPart();
        return toSSEFormat(finishPart);
    }
    
    /**
     * Creates the SSE DONE marker.
     * 
     * @return SSE DONE marker
     */
    public static String createDoneSSE() {
        return DATA_PREFIX + DONE_MARKER + "\n\n";
    }
}
