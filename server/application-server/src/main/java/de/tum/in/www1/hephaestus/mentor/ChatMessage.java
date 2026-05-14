package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Message in a conversation tree structure that maps to AI SDK UI Message format.
 * Each message can have multiple children (branches), but only one parent.
 *
 * Maps directly to the UIMessage type from the AI SDK
 */
@Entity
@Table(
    name = "chat_message",
    indexes = { @Index(name = "idx_chat_message_thread_created", columnList = "thread_id, created_at") }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessage {

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
     * Message metadata as a JSON object. Conventional keys (written by MentorChatService):
     * {@code status} ∈ {in_flight, completed, interrupted}, {@code model}, {@code inputTokens},
     * {@code outputTokens}, {@code cacheReadTokens}, {@code cacheWriteTokens}, {@code costUsd},
     * {@code finishReason}, {@code durationMs}, {@code toolCalls}. Shape is enforced by the
     * {@code chk_chat_message_metadata_shape} CHECK constraint (must be NULL or object).
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;

    @NonNull
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * AI SDK UIMessage parts as a JSONB array. Written verbatim by MentorChatService. Reads go
     * through {@code ChatThreadService.effectiveParts}, which falls back to {@link #legacyParts}
     * during the dual-write window. The fallback collapses once {@code chat_message_part} drops
     * in #1074. NOT NULL enforced at the column level (changeset {@code mentor-1071-enforce-parts-shape}) plus an array-shape
     * CHECK so a future writer that forgets to set parts cannot silently fall through to the legacy
     * path.
     */
    @Type(JsonType.class)
    @Column(name = "parts", columnDefinition = "jsonb", nullable = false)
    @ColumnDefault("'[]'::jsonb")
    private JsonNode parts;

    /**
     * Legacy normalised parts rows from the pre-Pi mentor era. Retained for read-path
     * back-compat; new writers go via the {@link #parts} JSONB. The
     * {@code chat_message_part} table is dropped in #1074.
     */
    @Deprecated(forRemoval = true)
    @OneToMany(mappedBy = "message", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("id.orderIndex ASC")
    @BatchSize(size = 50)
    @ToString.Exclude
    @JsonIgnore
    private List<ChatMessagePart> legacyParts = new ArrayList<>();

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
