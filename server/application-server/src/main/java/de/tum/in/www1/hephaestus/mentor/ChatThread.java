package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"allMessages", "selectedLeafMessage", "user"})
public class ChatThread {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @ToString.Exclude
    private List<ChatMessage> allMessages = new ArrayList<>();

    /**
     * Currently selected leaf message - represents the end of the active conversation path
     * The full selected path is computed by traversing parent relationships from this message
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_leaf_message_id")
    @ToString.Exclude
    private ChatMessage selectedLeafMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    /**
     * Gets the currently selected conversation path
     * This traces through the selected leaf message up to the root via parent relationships
     */
    public List<ChatMessage> getSelectedConversationPath() {
        if (selectedLeafMessage == null) {
            return new ArrayList<>();
        }
        return selectedLeafMessage.getPathFromRoot();
    }

    /**
     * Gets the latest message in the selected conversation path
     */
    public ChatMessage getLatestMessage() {
        return selectedLeafMessage;
    }

    /**
     * Helper method to add a message and maintain bidirectional relationship
     */
    public void addMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        allMessages.add(message);
        message.setThread(this);
    }

    /**
     * Helper method to remove a message and maintain bidirectional relationship
     */
    public void removeMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        allMessages.remove(message);
        message.setThread(null);
        
        // Clear selected leaf message if it's being removed
        if (selectedLeafMessage != null && selectedLeafMessage.equals(message)) {
            selectedLeafMessage = null;
        }
    }

    /**
     * Sets the selected leaf message and validates it belongs to this thread
     */
    public void setSelectedLeafMessage(ChatMessage message) {
        if (message != null && !message.getThread().equals(this)) {
            throw new IllegalArgumentException("Selected leaf message must belong to this thread");
        }
        this.selectedLeafMessage = message;
    }
}
