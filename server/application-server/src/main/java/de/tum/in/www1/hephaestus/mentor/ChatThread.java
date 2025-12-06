package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
     * Add a message and maintain bidirectional relationship
     */
    public void addMessage(ChatMessage message) {
        if (message == null) {
            return;
        }

        // Check if this message already exists in the collection by ID
        boolean exists = containsMessageWithId(message.getId());

        // Only add if not already in the collection
        if (!exists) {
            allMessages.add(message);
        }

        // Ensure bidirectional relationship, checking by ID to handle detached entities
        if (message.getThread() == null || !message.getThread().getId().equals(this.getId())) {
            message.setThread(this);
        }
    }

    /**
     * Remove a message and maintain relationships
     */
    public void removeMessage(ChatMessage message) {
        if (message == null) {
            return;
        }

        // Clear selected leaf message reference first if it's the message being removed
        if (selectedLeafMessage != null && selectedLeafMessage.getId().equals(message.getId())) {
            // Find a parent message to set as the new selected leaf, if available
            if (
                message.getParentMessage() != null &&
                allMessages.stream().anyMatch(m -> m.getId().equals(message.getParentMessage().getId()))
            ) {
                selectedLeafMessage = message.getParentMessage();
            } else {
                // Important: Set to null BEFORE removing the message to avoid FK constraint violations
                selectedLeafMessage = null;
            }
        }

        // Check for any children messages that reference this one as parent
        for (ChatMessage childMessage : allMessages) {
            if (
                childMessage.getParentMessage() != null &&
                childMessage.getParentMessage().getId().equals(message.getId())
            ) {
                childMessage.setParentMessage(message.getParentMessage());
            }
        }

        // Then remove from collection if it exists
        allMessages.removeIf(m -> m.getId().equals(message.getId()));

        // Break bidirectional relationship if this is the actual instance
        if (message.getThread() != null && message.getThread().getId().equals(this.getId())) {
            message.setThread(null);
        }
    }

    /**
     * Sets the selected leaf message
     */
    public void setSelectedLeafMessage(ChatMessage message) {
        if (message == null) {
            this.selectedLeafMessage = null;
            return;
        }

        // Make sure the message belongs to this thread
        if (message.getThread() == null) {
            message.setThread(this);
            if (!containsMessageWithId(message.getId())) {
                this.allMessages.add(message);
            }
        } else if (message.getThread().getId() == null || !message.getThread().getId().equals(this.getId())) {
            // If it belongs to another thread, we can update the reference to handle
            // detached entities or cases where message was persisted separately
            message.setThread(this);
            if (!containsMessageWithId(message.getId())) {
                this.allMessages.add(message);
            }
        }

        // Set it as the selected leaf message
        this.selectedLeafMessage = message;
    }

    /**
     * Check if a message with the given ID exists in this thread
     */
    public boolean containsMessageWithId(UUID messageId) {
        if (messageId == null) {
            return false;
        }
        return allMessages.stream().anyMatch(m -> messageId.equals(m.getId()));
    }

    /**
     * Convert the thread to a UI-compatible JSON representation
     */
    public JsonNode toUiJson() {
        ObjectNode threadJson = OBJECT_MAPPER.createObjectNode();
        threadJson.put("id", id.toString());
        threadJson.put("title", title);

        ArrayNode messagesArray = threadJson.putArray("messages");

        // Only include messages in the selected path
        List<ChatMessage> selectedPath = getSelectedConversationPath();
        for (ChatMessage message : selectedPath) {
            messagesArray.add(message.toUIMessageJson());
        }
        // In the future we will return the trees of messages once we support branching

        return threadJson;
    }
}
