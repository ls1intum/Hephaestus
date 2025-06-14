package de.tum.in.www1.hephaestus.mentor;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
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
    @JoinColumn(name = "message_id", nullable = false, insertable = false, updatable = false)
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
    @Column(name = "tool_invocation_json", columnDefinition = "JSONB")
    private String toolInvocationJson;

    /**
     * Source object serialized as JSON (maps to MessagePartsInner.source)  
     */
    @Column(name = "source_json", columnDefinition = "JSONB")
    private String sourceJson;

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
    @Column(name = "reasoning_details_json", columnDefinition = "JSONB")
    private String reasoningDetailsJson;

    public enum MessagePartType {
        TEXT("text"),
        REASONING("reasoning"),
        TOOL_INVOCATION("tool-invocation"),
        SOURCE("source"),
        FILE("file"),
        STEP_START("step-start");

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
