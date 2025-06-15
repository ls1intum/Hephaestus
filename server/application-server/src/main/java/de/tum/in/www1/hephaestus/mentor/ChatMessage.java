package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.*;
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
 * Message in a conversation tree structure that maps to AI SDK message format.
 * Each message can have multiple children (branches), but only one parent.
 * AI SDK works with a linear sequence extracted from this tree.
 * A message is composed of multiple parts, which can be used to handle complex content.
 * 
 * Maps directly to de.tum.in.www1.hephaestus.intelligenceservice.model.Message
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"thread", "parentMessage", "childMessages", "parts"})
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
    private ChatThread thread;

    /**
     * Parent message - null for root messages (enables tree structure)
     */
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    @ToString.Exclude
    private ChatMessage parentMessage;

    /**
     * Child messages - branches from this message
     */
    @OneToMany(mappedBy = "parentMessage", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, 
               fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    private List<ChatMessage> childMessages = new ArrayList<>();

    /**
     * Role of the message sender.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @NonNull
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Message parts - handles complex multi part content
     */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("id.orderIndex ASC")
    @ToString.Exclude
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

    /**
     * Gets the selected conversation path from root to this message
     */
    public List<ChatMessage> getPathFromRoot() {
        List<ChatMessage> path = new ArrayList<>();
        ChatMessage current = this;
        
        while (current != null) {
            path.add(0, current); // Add to beginning
            current = current.getParentMessage();
        }
        
        return path;
    }

    /**
     * Helper method to add a child message and maintain bidirectional relationship
     */
    public void addChildMessage(ChatMessage child) {
        if (child == null) {
            return;
        }
        childMessages.add(child);
        child.setParentMessage(this);
    }

    /**
     * Helper method to add a message part and maintain bidirectional relationship
     */
    public void addMessagePart(ChatMessagePart part) {
        if (part == null) {
            return;
        }
        parts.add(part);
        part.setMessage(this);
    }

    /**
     * Helper method to remove a message part and maintain bidirectional relationship
     */
    public void removeMessagePart(ChatMessagePart part) {
        if (part == null) {
            return;
        }
        parts.remove(part);
        part.setMessage(null);
    }

    /**
     * Sets the parent message and validates the relationship
     */
    public void setParentMessage(ChatMessage parent) {
        if (parent != null && parent.equals(this)) {
            throw new IllegalArgumentException("Message cannot be its own parent");
        }
        this.parentMessage = parent;
    }
}
