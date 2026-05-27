package de.tum.cit.aet.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
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
     * All messages in this thread. DB-side {@code ON DELETE CASCADE} (migration
     * {@code mentor-1071-cascade-legacy-fks}) covers parent-delete propagation in a single
     * statement; the JPA-level cascade would issue N per-row DELETEs for nothing. We keep
     * {@code orphanRemoval} for collection-mutation semantics.
     */
    @OneToMany(mappedBy = "thread", fetch = FetchType.LAZY, orphanRemoval = true)
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
     * Verbatim Pi SDK session JSONL bytes. BYTEA, plain {@code byte[]} — NOT {@code @Lob},
     * which would force Postgres OID mode and break auto-commit reads. Bulk reads go through
     * {@code ChatThreadRepository#findSessionJsonl}.
     */
    @Column(name = "session_jsonl", columnDefinition = "bytea")
    @ToString.Exclude
    @JsonIgnore
    private byte[] sessionJsonl;
}
