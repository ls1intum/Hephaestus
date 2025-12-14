package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

/** Chat message part stored as JSON content; maps to AI SDK UI message parts. */
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

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartType type;

    @Column(length = 128)
    private String originalType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode content;

    public enum PartType {
        TEXT("text"),
        REASONING("reasoning"),
        TOOL("tool"),
        SOURCE_URL("source-url"),
        SOURCE_DOCUMENT("source-document"),
        FILE("file"),
        DATA("data"),
        STEP_START("step-start");

        private final String value;

        PartType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static PartType fromValue(String typeString) {
            if (typeString == null) throw new IllegalArgumentException("Type cannot be null");
            if (typeString.startsWith("tool-") && typeString.length() > 5) return TOOL;
            if (typeString.startsWith("data-") && typeString.length() > 5) return DATA;
            for (PartType t : values()) if (t.value.equals(typeString)) return t;
            throw new IllegalArgumentException("Unknown message part type: " + typeString);
        }
    }
}
