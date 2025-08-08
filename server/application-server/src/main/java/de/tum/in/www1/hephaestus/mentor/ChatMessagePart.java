package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Input;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Output;
import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessagePartsInner;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.springframework.lang.NonNull;

/**
 * Represents a part of a chat message in the AI SDK Data Stream Protocol.
 * Maps directly to the various UI message part types in the AI SDK.
 */
@Entity
@Table(name = "chat_message_part")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessagePart {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private ChatMessagePartId id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "messageId", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    @JsonIgnore
    private ChatMessage message;

    /**
     * The canonical type of the message part, matching the AI SDK UI part type system.
     * For custom types like tool-{name} or data-{type}, use TOOL or DATA respectively
     * and the specific type will be stored in the content.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartType type;

    /**
     * Original type string from the UI message part, preserved for exact matching.
     * This is especially important for tool-{name} and data-{type} formats.
     */
    @Column(length = 128)
    private String originalType;

    /**
     * The JSON content payload for this message part, stored as JSONB.
     * Structure varies based on the {@link PartType}.
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode content;

    /**
     * Enum representing all possible message part types from the AI SDK v5 UI message system.
     * Maps directly to the database constraint values and Python models.py.
     */
    public enum PartType {
        // Core message part types
        TEXT("text"),
        REASONING("reasoning"),

        // Tool-related parts - single "tool" type with states in content
        TOOL("tool"), // For tool-{name} with states: input-streaming, input-available, output-available, output-error

        // Source reference parts
        SOURCE_URL("source-url"),
        SOURCE_DOCUMENT("source-document"),

        // File part
        FILE("file"),

        // Data part - for data-{type} pattern
        DATA("data"),

        // Step control
        STEP_START("step-start");

        private final String value;

        PartType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Parse a type string from the UI system into our enum.
         * Handles special cases like tool-{name} and data-{type}.
         */
        public static PartType fromValue(String typeString) {
            if (typeString == null) {
                throw new IllegalArgumentException("Type cannot be null");
            }

            // Handle tool-{name} pattern
            if (typeString.startsWith("tool-") && typeString.length() > 5) {
                return TOOL;
            }

            // Handle data-{type} pattern
            if (typeString.startsWith("data-") && typeString.length() > 5) {
                return DATA;
            }

            // Handle standard types
            for (PartType type : values()) {
                if (type.value.equals(typeString)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Unknown message part type: " + typeString);
        }
    }

    /**
     * Get the specific tool name if this is a tool part
     * @return Tool name or null if this isn't a tool part
     */
    public String getToolName() {
        if (isToolPart() && originalType != null && originalType.startsWith("tool-")) {
            return originalType.substring(5);
        }
        return null;
    }

    public String getToolCallId() {
        if (isToolPart() && content != null && content.has("toolCallId")) {
            return content.get("toolCallId").asText();
        }
        return null;
    }

    /**
     * Get the specific data type if this is a data part
     * @return Data type or null if this isn't a data part
     */
    public String getDataType() {
        if (type == PartType.DATA && originalType != null && originalType.startsWith("data-")) {
            return originalType.substring(5);
        }
        return null;
    }

    /**
     * Get the tool state if this is a tool part
     * @return tool state if this is a tool part, null otherwise
     */
    public String getToolState() {
        if (isToolPart() && content != null && content.has("state")) {
            return content.get("state").asText();
        }
        return null;
    }

    /**
     * Convenience method to check if this part contains a tool call result
     */
    public boolean isToolResult() {
        return type == PartType.TOOL && "output-available".equals(getToolState());
    }

    /**
     * Helper method to check if this is any kind of tool part
     */
    public boolean isToolPart() {
        return type == PartType.TOOL;
    }

    /**
     * Convenience method to get message ID from the embedded ID
     */
    public UUID getMessageId() {
        return id != null ? id.getMessageId() : null;
    }

    /**
     * Convenience method to get order index from the embedded ID
     */
    public Integer getOrderIndex() {
        return id != null ? id.getOrderIndex() : null;
    }

    /**
     * Convenience method to set message ID by creating a new embedded ID
     */
    public void setMessageId(UUID messageId) {
        Integer currentOrderIndex = this.id != null ? this.id.getOrderIndex() : null;
        this.id = new ChatMessagePartId(messageId, currentOrderIndex);
    }

    /**
     * Convenience method to set order index by creating a new embedded ID
     */
    public void setOrderIndex(Integer orderIndex) {
        UUID currentMessageId = this.id != null ? this.id.getMessageId() : null;
        this.id = new ChatMessagePartId(currentMessageId, orderIndex);
    }

    /**
     * Convert this message part to a UIMessagePartsInner object.
     * Handles the mapping from internal JSON structure to the UI model.
     *
     * @return A UIMessagePartsInner representation of this message part
     */
    public UIMessagePartsInner toUIMessagePart() {
        try {
            // Handle null content
            if (content == null) {
                UIMessagePartsInner uiPart = new UIMessagePartsInner();
                uiPart.setType(originalType != null ? originalType : type.getValue());
                // Clear default values that shouldn't be set
                uiPart.setState(null);
                return uiPart;
            }

            // Create the UI part manually to handle field mapping correctly
            UIMessagePartsInner uiPart = new UIMessagePartsInner();

            // Set the type correctly
            String partType = originalType != null ? originalType : type.getValue();
            uiPart.setType(partType);

            // Handle different part types
            if (type == PartType.TEXT || type == PartType.REASONING) {
                // For text and reasoning parts
                if (content.has("text")) {
                    uiPart.setText(content.get("text").asText());
                }
                uiPart.setState(null); // Clear default state for text/reasoning parts
            } else if (type == PartType.TOOL) {
                // For tool parts, handle the state and data fields correctly
                String toolState = null;
                if (content.has("state")) {
                    toolState = content.get("state").asText();
                    uiPart.setState(toolState);
                }

                if (content.has("toolCallId")) {
                    uiPart.setToolCallId(content.get("toolCallId").asText());
                }

                // Handle fields based on the specific tool state to match Python model requirements
                if ("input-streaming".equals(toolState)) {
                    // ToolInputStreamingPart: input (optional), providerExecuted (optional)
                    if (content.has("input") && !content.get("input").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            Input inputValue = mapper.treeToValue(content.get("input"), Input.class);
                            uiPart.setInput(inputValue);
                        } catch (Exception e) {
                            // Keep input as null for input-streaming if conversion fails
                        }
                    }
                } else if ("input-available".equals(toolState)) {
                    // ToolInputAvailablePart: input (required), providerExecuted (optional)
                    Input inputValue = null;
                    if (content.has("args") && !content.get("args").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            inputValue = mapper.treeToValue(content.get("args"), Input.class);
                        } catch (Exception e) {
                            inputValue = new Input();
                        }
                    } else if (content.has("input") && !content.get("input").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            inputValue = mapper.treeToValue(content.get("input"), Input.class);
                        } catch (Exception e) {
                            inputValue = new Input();
                        }
                    } else {
                        inputValue = new Input(); // Required field
                    }
                    uiPart.setInput(inputValue);
                } else if ("output-available".equals(toolState)) {
                    // ToolOutputAvailablePart: input (required), output (required), providerExecuted (optional)

                    // Handle required input field
                    Input inputValue = null;
                    if (content.has("args") && !content.get("args").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            inputValue = mapper.treeToValue(content.get("args"), Input.class);
                        } catch (Exception e) {
                            inputValue = new Input();
                        }
                    } else if (content.has("input") && !content.get("input").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            inputValue = mapper.treeToValue(content.get("input"), Input.class);
                        } catch (Exception e) {
                            inputValue = new Input();
                        }
                    } else {
                        inputValue = new Input(); // Required field
                    }
                    uiPart.setInput(inputValue);

                    // Handle required output field
                    Output outputValue = null;
                    if (content.has("result") && !content.get("result").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            outputValue = mapper.treeToValue(content.get("result"), Output.class);
                        } catch (Exception e) {
                            outputValue = new Output();
                        }
                    } else if (content.has("output") && !content.get("output").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            outputValue = mapper.treeToValue(content.get("output"), Output.class);
                        } catch (Exception e) {
                            outputValue = new Output();
                        }
                    } else {
                        outputValue = new Output(); // Required field
                    }
                    uiPart.setOutput(outputValue);
                } else if ("output-error".equals(toolState)) {
                    // ToolOutputErrorPart: input (required), errorText (required), providerExecuted (optional)

                    // Handle required input field
                    Input inputValue = null;
                    if (content.has("args") && !content.get("args").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            inputValue = mapper.treeToValue(content.get("args"), Input.class);
                        } catch (Exception e) {
                            inputValue = new Input();
                        }
                    } else if (content.has("input") && !content.get("input").isNull()) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            inputValue = mapper.treeToValue(content.get("input"), Input.class);
                        } catch (Exception e) {
                            inputValue = new Input();
                        }
                    } else {
                        inputValue = new Input(); // Required field
                    }
                    uiPart.setInput(inputValue);

                    // Handle required errorText field
                    if (content.has("errorText") && !content.get("errorText").isNull()) {
                        uiPart.setErrorText(content.get("errorText").asText());
                    } else {
                        uiPart.setErrorText("An error occurred during tool execution"); // Required field
                    }
                }

                // Handle providerExecuted if present (optional for all tool states)
                if (content.has("providerExecuted") && !content.get("providerExecuted").isNull()) {
                    uiPart.setProviderExecuted(content.get("providerExecuted").asBoolean());
                }
            } else if (type == PartType.FILE) {
                // For file parts
                if (content.has("mediaType")) {
                    uiPart.setMediaType(content.get("mediaType").asText());
                }
                if (content.has("url")) {
                    uiPart.setUrl(content.get("url").asText());
                }
                if (content.has("filename")) {
                    uiPart.setFilename(content.get("filename").asText());
                }
                uiPart.setState(null); // Clear default state
            } else if (type == PartType.DATA) {
                // For data parts
                if (content.has("data")) {
                    uiPart.setData(content.get("data"));
                }
                if (content.has("id")) {
                    uiPart.setId(content.get("id").asText());
                }
                uiPart.setState(null); // Clear default state
            } else {
                // For other part types, try to convert safely
                uiPart.setState(null); // Clear default state

                // Copy over common fields if they exist
                if (content.has("text")) {
                    uiPart.setText(content.get("text").asText());
                }
                if (content.has("state")) {
                    uiPart.setState(content.get("state").asText());
                }
            }

            // Copy over provider metadata if present
            if (content.has("providerMetadata")) {
                uiPart.setProviderMetadata(content.get("providerMetadata"));
            }

            return uiPart;
        } catch (Exception e) {
            // Log the conversion error for debugging
            System.err.println("Failed to convert ChatMessagePart to UIMessagePart: " + e.getMessage());
            System.err.println("Content: " + content);
            System.err.println("Type: " + type + ", OriginalType: " + originalType);
            e.printStackTrace();

            // Create a fallback instance
            UIMessagePartsInner uiPart = new UIMessagePartsInner();
            String partType = originalType != null ? originalType : type.getValue();
            uiPart.setType(partType);
            uiPart.setState(null); // Clear default state

            // For text parts, try to extract the text directly from content
            if (content != null && content.has("text")) {
                uiPart.setText(content.get("text").asText());
            }

            return uiPart;
        }
    }
}
