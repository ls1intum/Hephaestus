package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.NonNull;
import java.util.UUID;

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
public class ChatThread {
    
    @Id
    private UUID id;

    @NonNull
    @CreationTimestamp
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String title;

    /**
     * All messages in this thread (tree structure)
     */
    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ChatMessage> allMessages = new ArrayList<>();

    /**
     * Currently selected leaf message - represents the end of the active conversation path
     * The full selected path is computed by traversing parent relationships from this message
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_leaf_message_id")
    @ToString.Exclude
    private ChatMessage selectedLeafMessage;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    /**
     * Gets the currently selected conversation path
     * This traces through the selected leaf message up to the root via parent relationships
     */
    public List<ChatMessage> getSelectedConversationPath() {
        return selectedLeafMessage.getPathFromRoot();
    }

    /**
     * Gets the latest message in the selected conversation path
     */
    public ChatMessage getLatestMessage() {
        return selectedLeafMessage;
    }
}
