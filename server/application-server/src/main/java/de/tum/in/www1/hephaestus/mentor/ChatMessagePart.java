package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        TOOL("tool"),  // For tool-{name} with states: input-streaming, input-available, output-available, output-error

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
     * Uses ObjectMapper to convert the content JSON to the appropriate UI model.
     * 
     * @return A UIMessagePartsInner representation of this message part
     */
    public UIMessagePartsInner toUIMessagePart() {    
        try {
            // Start with a copy of the content as the base
            UIMessagePartsInner uiPart = objectMapper.treeToValue(content, UIMessagePartsInner.class);
            
            // Ensure the type is set correctly
            // Use originalType if available, otherwise use the canonical type value
            String partType = originalType != null ? originalType : type.getValue();
            uiPart.setType(partType);
            
            return uiPart;
        } catch (Exception e) {
            // If conversion fails, create a new instance with just the type
            UIMessagePartsInner uiPart = new UIMessagePartsInner();
            uiPart.setType(originalType != null ? originalType : type.getValue());
            return uiPart;
        }
    }
}
