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
     * Raw {@code parent_message_id} FK exposed as a {@link UUID} that does NOT trigger Hibernate
     * lazy-load of the parent entity. Used by {@link ChatMessageDTO#from} so listing a thread
     * with N messages issues 1 query, not 1 + N (lazy proxy per message). {@code insertable} /
     * {@code updatable} = false because the {@link #parentMessage} relation owns the write side.
     */
    @Nullable
    @Column(name = "parent_message_id", insertable = false, updatable = false)
    private UUID parentMessageId;

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
     * Turn lifecycle status. The only correctness-critical metadata field: drives the partial
     * unique index {@code ux_chat_message_in_flight_v2} (one in_flight row per thread) and
     * the {@code MentorInFlightReaper} stuck-row sweep. Promoted from
     * {@code metadata.status} JSONB to a real column in migration {@code mentor-1071-add-status-column}.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.in_flight;

    /**
     * Message metadata as a JSON object. Conventional keys (written by MentorChatService):
     * {@code model}, {@code inputTokens}, {@code outputTokens}, {@code cacheReadTokens},
     * {@code cacheWriteTokens}, {@code costUsd}, {@code finishReason}, {@code durationMs},
     * {@code toolCalls}, {@code error}. Pure billing/observability — status is its own column.
     * Shape is enforced by the {@code chk_chat_message_metadata_shape} CHECK constraint
     * (must be NULL or object).
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

    /**
     * Optimistic-lock version. Bumped by Hibernate on every managed write; a
     * {@code @Modifying} UPDATE (e.g. {@link ChatMessageRepository#reapStaleInFlight}) bumps it
     * explicitly. Race scenarios covered:
     * <ul>
     *   <li><b>Reaper-vs-finalise</b>: reaper flips a stuck row to {@code interrupted} at
     *       version=N; meanwhile the runner's late {@code finalise} re-saves the same row from
     *       a stale (version=N-1) snapshot. Hibernate detects the version mismatch and throws
     *       {@code OptimisticLockingFailureException}; the orchestrator logs and skips so the
     *       reaper's verdict survives.</li>
     *   <li><b>Finalise-vs-interrupt</b>: a successful finalise (version=N+1) is followed by a
     *       stale-handler interrupt; same exception, same skip — no downgrade.</li>
     * </ul>
     * Replaces the prior {@code isStillInFlight} read-before-write TOCTOU check.
     */
    @org.hibernate.annotations.ColumnDefault("0")
    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long version;

    /**
     * Turn lifecycle status. Stored as VARCHAR(16) with a CHECK constraint enforcing the enum
     * values. Lowercase names match the historical metadata.status JSON values verbatim so
     * the backfill UPDATE is a one-liner (see migration {@code mentor-1071-backfill-status}).
     */
    public enum Status {
        in_flight,
        completed,
        interrupted,
    }

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
