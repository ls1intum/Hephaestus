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
    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @ToString.Exclude
    @JsonIgnore
    private List<ChatMessage> allMessages = new ArrayList<>();

    /**
     * Currently selected leaf message - represents the end of the active conversation path
     * The full selected path is computed by traversing parent relationships from this message
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_leaf_message_id")
    @ToString.Exclude
    @JsonIgnore
    private ChatMessage selectedLeafMessage;

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
}
