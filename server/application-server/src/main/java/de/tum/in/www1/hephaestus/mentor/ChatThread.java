package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
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
import org.springframework.lang.NonNull;

/**
 * Simple conversation thread for AI SDK that contains a tree of messages.
 * The AI SDK extracts uses a linear conversation path from this tree structure.
 */
@Entity
@Table(name = "chat_thread")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatThread {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @NonNull
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT", length = 256)
    @Size(max = 256)
    private String title;

    /**
     * All messages in this thread (tree structure)
     */
    @OneToMany(mappedBy = "thread", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @ToString.Exclude
    @JsonIgnore
    private List<ChatMessage> allMessages = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @JsonIgnore
    private User user;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Workspace workspace;

    /**
     * Pi SDK session JSONL bytes, persisted verbatim per turn so a cold container can
     * restore byte-identical state — critical for provider prompt caching (Anthropic +
     * OpenAI key on byte-identical prefix), tool-call / tool-result pairing (Anthropic
     * 400s on orphaned tool_use), and thinking blocks (extended-thinking models require
     * verbatim preservation).
     *
     * <p>Written by {@code MentorTurnPersistence#finalise} in the same REQUIRES_NEW
     * transaction as the assistant row, so the column and {@code chat_message.status}
     * never diverge by more than one in-flight crash window.
     *
     * <p>For most reads ({@code /threads} list, message rehydration) this column is
     * unused; lookups go through {@code ChatThreadRepository#findSessionJsonl} which
     * bypasses the entity. JSON-ignored so it never leaves the JVM.
     *
     * <p><strong>Storage shape:</strong> Postgres BYTEA (NOT pg_largeobject). NO
     * {@code @Lob} annotation: on Postgres + Hibernate, {@code @Lob byte[]} switches the
     * driver to large-object (OID) mode which requires explicit transactions and throws
     * {@code "Large Objects may not be used in auto-commit mode"} on simple reads. A plain
     * {@code byte[]} column maps directly to BYTEA (with TOAST compression transparently
     * handling multi-KB payloads), exactly what the migration creates.
     */
    @Column(name = "session_jsonl", columnDefinition = "bytea")
    @ToString.Exclude
    @JsonIgnore
    private byte[] sessionJsonl;
}
