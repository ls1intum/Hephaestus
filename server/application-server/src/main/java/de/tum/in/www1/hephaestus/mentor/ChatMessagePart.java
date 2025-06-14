package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.NonNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps directly to de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner
 */
@Entity
@Table(name = "chat_message_part")
@IdClass(ChatMessagePartId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ChatMessagePart {
    
    @Id
    private UUID messageId;
    
    @Id
    @Min(0)
    private Integer orderIndex;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "messageId", insertable = false, updatable = false)
    @ToString.Exclude
    private ChatMessage message;

    @NonNull
    @CreationTimestamp
    private Instant createdAt;

    /**
     * Part type - Valid values: text, reasoning, tool-invocation, source, file, step-start
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessagePartType type;

    /**
     * Text content - REQUIRED ONLY for TEXT part types (maps to MessagePartsInner.text)
     * For other types, this field may be null or empty
     */
    @Column(columnDefinition = "TEXT")
    private String text;

    /**
     * Reasoning text for reasoning parts (maps to MessagePartsInner.reasoning)
     */
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /**
     * Tool invocation object serialized as JSON (maps to MessagePartsInner.toolInvocation)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode toolInvocationJson;

    /**
     * Source object serialized as JSON (maps to MessagePartsInner.source)  
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode sourceJson;

    /**
     * Generic data field (maps to MessagePartsInner.data)
     */
    @Column(columnDefinition = "TEXT")
    private String data;

    /**
     * MIME type for file parts (maps to MessagePartsInner.mimeType)
     */
    @Size(max = 255)
    @Column(length = 255)
    private String mimeType;

    /**
     * Reasoning details list serialized as JSON (maps to MessagePartsInner.details)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode reasoningDetailsJson;

    /**
     * Tool call ID for tool-related parts
     */
    @Size(max = 255)
    @Column(name = "tool_call_id", length = 255)
    private String toolCallId;

    /**
     * Tool name for tool invocation parts
     */
    @Size(max = 255)
    @Column(name = "tool_name", length = 255)
    private String toolName;

    /**
     * Generic content field for flexible content storage
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Part order within the message for proper sequencing
     */
    @Column(name = "part_order")
    private Integer partOrder;

    public enum MessagePartType {
        TEXT("text"),                           // 0: frame - text content
        REASONING("reasoning"),                 // g: frame - reasoning content  
        REASONING_SIGNATURE("reasoning-signature"), // j: frame - reasoning signature
        REASONING_REDACTED("reasoning-redacted"), // i: frame - redacted reasoning
        TOOL_INVOCATION("tool-invocation"),     // 9: frame - complete tool call
        TOOL_STREAMING_START("tool-streaming-start"), // b: frame - tool call start
        TOOL_DELTA("tool-delta"),              // c: frame - tool call args delta
        TOOL_RESULT("tool-result"),            // a: frame - tool call result
        DATA("data"),                          // 2: frame - structured data
        ANNOTATION("annotation"),              // 8: frame - message annotations
        ERROR("error"),                        // 3: frame - error content
        FILE("file"),                          // k: frame - file attachment
        SOURCE("source"),                      // h: frame - source citation
        STEP_START("step-start"),              // f: frame - step boundary
        STEP_FINISH("step-finish"),            // e: frame - step completion
        MESSAGE_FINISH("message-finish");       // d: frame - message - completion

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
     * Get the composite primary key
     */
    public ChatMessagePartId getId() {
        return new ChatMessagePartId(messageId, orderIndex);
    }
    
    /**
     * Set the composite primary key
     */
    public void setId(ChatMessagePartId id) {
        if (id != null) {
            this.messageId = id.getMessageId();
            this.orderIndex = id.getOrderIndex();
        }
    }
}
