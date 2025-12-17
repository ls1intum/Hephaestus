package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.springframework.lang.NonNull;

/**
 * Message in a conversation tree structure that maps to AI SDK UI Message format.
 * Each message can have multiple children (branches), but only one parent.
 *
 * Maps directly to the UIMessage type from the AI SDK
 */
@Entity
@Table(
    name = "chat_message",
    indexes = { @Index(name = "idx_chat_message_thread_created", columnList = "thread_id, createdAt") }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Thread this message belongs to.
     */
    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private ChatThread thread;

    /**
     * Parent message - null for root messages (enables tree structure)
     */
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    @ToString.Exclude
    @JsonIgnore
    private ChatMessage parentMessage;

    /**
     * Child messages - branches from this message
     */
    @OneToMany(
        mappedBy = "parentMessage",
        cascade = { CascadeType.PERSIST, CascadeType.MERGE },
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    @ToString.Exclude
    @JsonIgnore
    private List<ChatMessage> childMessages = new ArrayList<>();

    /**
     * Role of the message sender.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    /**
     * Optional message metadata in JSON format
     * Can contain any structure as defined in UIMessage<METADATA>
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;

    @NonNull
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Message parts - handles complex multi-part content
     * Each part corresponds to a UIMessagePart type
     */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("id.orderIndex ASC")
    @ToString.Exclude
    @JsonIgnore
    private List<ChatMessagePart> parts = new ArrayList<>();

    public enum Role {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Role fromValue(String value) {
            for (Role role : values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Unknown role: " + value);
        }
    }
}
