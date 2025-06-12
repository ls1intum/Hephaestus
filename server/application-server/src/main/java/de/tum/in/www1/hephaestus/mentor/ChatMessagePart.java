package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.NonNull;
import java.time.Instant;

/**
 * Maps directly to de.tum.in.www1.hephaestus.intelligenceservice.model.MessagePartsInner
 */
@Entity
@Table(name = "chat_message_part", indexes = {
    @Index(name = "idx_message_part_message_order", columnList = "message_id, orderIndex"),
    @Index(name = "idx_message_part_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@ToString
@Slf4j
public class ChatMessagePart {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    @ToString.Exclude
    private ChatMessage message;

    @NonNull
    @CreationTimestamp
    private Instant createdAt;

    /**
     * Order of this part within the message (0-based)
     */
    @NonNull
    @Min(0)
    private Integer orderIndex;

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
}
