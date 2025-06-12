package de.tum.in.www1.hephaestus.mentor;

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
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Thread this message belongs to.
     */
    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    @ToString.Exclude
    private ChatThread thread;

    /**
     * Parent message - null for root messages (enables tree structure)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    @ToString.Exclude
    private ChatMessage parentMessage;

    /**
     * Child messages - branches from this message
     */
    @OneToMany(mappedBy = "parentMessage", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ChatMessage> childMessages = new ArrayList<>();

    /**
     * Role of the message sender.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    private Role role;

    @NonNull
    @CreationTimestamp
    private Instant createdAt;

    /**
     * Message parts - handles complex multi part content
     */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
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
}
