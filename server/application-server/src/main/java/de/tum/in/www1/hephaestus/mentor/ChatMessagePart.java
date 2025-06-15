package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.springframework.lang.NonNull;
import java.util.UUID;

/**
 * Maps directly to de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner
 */
@Entity
@Table(name = "chat_message_part")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"message"})
public class ChatMessagePart {
    
    @EmbeddedId
    @EqualsAndHashCode.Include
    private ChatMessagePartId id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "messageId", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private ChatMessage message;

    /**
     * Part type - Valid values: text, reasoning, tool-invocation, source, file, step-start
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MessagePartType type;

    /**
     * The JSON content payload for this message part, stored as JSONB.
     * 
     * The structure and type of this JSON content varies based on the {@link MessagePartType}.
     * This follows the AI SDK Data Stream Protocol specification for consistent streaming and processing.
     * 
     * NOTE: Not all message part types are typically persisted. Some are used primarily for 
     * streaming control and may be filtered out before database storage.
     * 
     * Content Structure by Message Part Type:
     * 
     * === Core Persistent Content ===
     * 
     * TEXT: Simple string content (PERSISTED)
     *   Example: "Hello world"
     * 
     * REASONING: AI reasoning explanation string (PERSISTED)
     *   Example: "I will analyze this step by step"
     * 
     * TOOL_INVOCATION: Complete tool call with arguments (PERSISTED)
     *   Example: {"toolCallId": "call-123", "toolName": "calculator", "args": {"operation": "add", "values": [1, 2]}}
     * 
     * TOOL_RESULT: Tool execution result (PERSISTED)
     *   Example: {"toolCallId": "call-123", "result": "3"}
     * 
     * DATA: Array of structured JSON objects (PERSISTED)
     *   Example: [{"key": "object1", "value": 123}, {"anotherKey": "object2", "count": 5}]
     * 
     * SOURCE: External source reference (PERSISTED)
     *   Example: {"sourceType": "url", "id": "src-001", "url": "https://example.com/article", "title": "Example Article"}
     * 
     * FILE: File attachment with base64 encoded data (PERSISTED - but see note)
     *   Example: {"data": "iVBORw0KGgoAAAANSUhEUgA...", "mimeType": "image/png"}
     *   Note: In the future we will store files in object storage and only keep a reference here.
     * 
     * === Streaming Control (Often Not Persisted) ===
     * 
     * TOOL_STREAMING_START: Tool call initialization (STREAMING ONLY)
     *   Example: {"toolCallId": "call-456", "toolName": "streaming-tool"}
     *   Usage: Signals start of streaming tool call, typically not stored
     * 
     * TOOL_DELTA: Partial tool argument updates during streaming (STREAMING ONLY)
     *   Example: {"toolCallId": "call-456", "argsTextDelta": "partial argument text"}
     *   Usage: Progressive updates during streaming, replaced by final TOOL_INVOCATION
     * 
     * STEP_START: Step execution boundary marker (STREAMING ONLY)
     *   Example: {"messageId": "step_123"}
     *   Usage: Marks beginning of processing step, typically not stored
     * 
     * STEP_FINISH: Step completion with usage statistics (METADATA)
     *   Example: {"finishReason": "stop", "usage": {"promptTokens": 150, "completionTokens": 75}, "isContinued": false}
     *   Usage: May be stored for analytics, but not essential for message reconstruction
     * 
     * MESSAGE_FINISH: Final message completion metadata (METADATA)
     *   Example: {"finishReason": "stop", "usage": {"promptTokens": 300, "completionTokens": 150}}
     *   Usage: May be stored for analytics, but not essential for message reconstruction
     * 
     * === Security & Metadata (Conditional) ===
     * 
     * REASONING_SIGNATURE: Signature verification object (CONDITIONAL)
     *   Example: {"signature": "abc123xyz"}
     *   Usage: Only needed if reasoning verification is required
     * 
     * REASONING_REDACTED: Redacted reasoning content (CONDITIONAL)
     *   Example: {"data": "This reasoning has been redacted for security"}
     *   Usage: Alternative to REASONING when content needs redaction
     * 
     * ANNOTATION: Array of message annotations (CONDITIONAL)
     *   Example: [{"id": "msg-123", "type": "highlight", "text": "Important note"}]
     *   Usage: UI-specific annotations, may not need persistence
     * 
     * ERROR: Error message string (CONDITIONAL)
     *   Example: "Connection timeout after 30 seconds"
     *   Usage: Typically logged separately, may not need message-level persistence
     * 
     * @see <a href="https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol">AI SDK Data Stream Protocol</a>
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    @ToString.Exclude
    private JsonNode content;

    public enum MessagePartType {
        TEXT("text"),                                 // 0: frame - text content
        REASONING("reasoning"),                       // g: frame - reasoning content  
        REASONING_SIGNATURE("reasoning-signature"),   // j: frame - reasoning signature
        REASONING_REDACTED("reasoning-redacted"),     // i: frame - redacted reasoning
        TOOL_INVOCATION("tool-invocation"),           // 9: frame - complete tool call
        TOOL_STREAMING_START("tool-streaming-start"), // b: frame - tool call start
        TOOL_DELTA("tool-delta"),                     // c: frame - tool call args delta
        TOOL_RESULT("tool-result"),                   // a: frame - tool call result
        DATA("data"),                                 // 2: frame - structured data
        ANNOTATION("annotation"),                     // 8: frame - message annotations
        ERROR("error"),                               // 3: frame - error content
        FILE("file"),                                 // k: frame - file attachment
        SOURCE("source"),                             // h: frame - source citation
        STEP_START("step-start"),                     // f: frame - step boundary
        STEP_FINISH("step-finish"),                   // e: frame - step completion
        MESSAGE_FINISH("message-finish");             // d: frame - message - completion

        private final String value;

        MessagePartType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static MessagePartType fromValue(String value) {
            for (MessagePartType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown message part type: " + value);
        }
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
}
