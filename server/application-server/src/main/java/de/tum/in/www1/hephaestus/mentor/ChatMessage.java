package de.tum.in.www1.hephaestus.mentor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessage;
import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessagePartsInner;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.springframework.lang.NonNull;

/**
 * Message in a conversation tree structure that maps to AI SDK UI Message format.
 * Each message can have multiple children (branches), but only one parent.
 *
 * Maps directly to the UIMessage type from the AI SDK
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatMessage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
     * Optional message metadata in JSON format
     * Can contain any structure as defined in UIMessage<METADATA>
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;

    @NonNull
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Message parts - handles complex multi-part content
     * Each part corresponds to a UIMessagePart type
     */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("id.orderIndex ASC")
    @ToString.Exclude
    @JsonIgnore
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
     * Adds a text part to the message
     */
    public ChatMessagePart addTextPart(String text) {
        ChatMessagePart part = ChatMessagePartFactory.createTextPart(this.id, this.parts.size(), text);
        addMessagePart(part);
        return part;
    }

    /**
     * Adds a reasoning part to the message
     */
    public ChatMessagePart addReasoningPart(String text) {
        ChatMessagePart part = ChatMessagePartFactory.createReasoningPart(this.id, this.parts.size(), text, null);
        addMessagePart(part);
        return part;
    }

    /**
     * Adds a tool call part to the message in "call" state
     */
    public ChatMessagePart addToolCallPart(String toolName, String toolCallId, Object args) {
        ChatMessagePart part = ChatMessagePartFactory.createToolCallPart(
            this.id,
            this.parts.size(),
            toolName,
            toolCallId,
            args
        );
        addMessagePart(part);
        return part;
    }

    /**
     * Adds a tool result part to the message
     */
    public ChatMessagePart addToolResultPart(String toolName, String toolCallId, Object args, Object result) {
        ChatMessagePart part = ChatMessagePartFactory.createToolResultPart(
            this.id,
            this.parts.size(),
            toolName,
            toolCallId,
            args,
            result
        );
        addMessagePart(part);
        return part;
    }

    /**
     * Adds a file part to the message
     */
    public ChatMessagePart addFilePart(String mediaType, String url, @Nullable String filename) {
        ChatMessagePart part = ChatMessagePartFactory.createFilePart(
            this.id,
            this.parts.size(),
            mediaType,
            url,
            filename
        );
        addMessagePart(part);
        return part;
    }

    /**
     * Adds a data part to the message
     */
    public ChatMessagePart addDataPart(String dataType, Object data, @Nullable String id) {
        ChatMessagePart part = ChatMessagePartFactory.createDataPart(this.id, this.parts.size(), dataType, data, id);
        addMessagePart(part);
        return part;
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
        child.setThread(this.thread);
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

        // Ensure the messageId is set correctly in the part's ID
        if (part.getId() == null || !this.id.equals(part.getMessageId())) {
            part.setMessageId(this.id);
        }

        // Ensure the orderIndex is set correctly
        if (part.getOrderIndex() == null || part.getOrderIndex() != parts.size() - 1) {
            part.setOrderIndex(parts.size() - 1);
        }
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

    /**
     * Find message parts by type
     */
    public List<ChatMessagePart> findPartsByType(ChatMessagePart.PartType type) {
        return parts.stream().filter(part -> part.getType() == type).collect(Collectors.toList());
    }

    /**
     * Find a message part by tool name
     */
    @Nullable
    public ChatMessagePart findToolPart(String toolName) {
        return parts
            .stream()
            .filter(part -> part.getType() == ChatMessagePart.PartType.TOOL && toolName.equals(part.getToolName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Find all tool parts for a specific tool
     */
    public List<ChatMessagePart> findToolParts(String toolName) {
        return parts
            .stream()
            .filter(part -> part.getType() == ChatMessagePart.PartType.TOOL && toolName.equals(part.getToolName()))
            .collect(Collectors.toList());
    }

    /**
     * Find a message part by data type
     */
    @Nullable
    public ChatMessagePart findDataPart(String dataType) {
        return parts
            .stream()
            .filter(part -> part.getType() == ChatMessagePart.PartType.DATA && dataType.equals(part.getDataType()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Convert this message to a UIMessage JSON representation
     */
    public JsonNode toUIMessageJson() {
        ObjectNode messageJson = OBJECT_MAPPER.createObjectNode();
        messageJson.put("id", id.toString());
        messageJson.put("role", role.getValue());

        if (metadata != null) {
            messageJson.set("metadata", metadata);
        }

        ArrayNode partsArray = messageJson.putArray("parts");
        for (ChatMessagePart part : parts) {
            partsArray.add(part.getContent());
        }

        return messageJson;
    }

    /**
     * Convert this message to a UIMessage model object
     */
    public UIMessage toUIMessage() {
        UIMessage uiMessage = new UIMessage();
        uiMessage.setId(id.toString());
        uiMessage.setRole(UIMessage.RoleEnum.fromValue(role.getValue()));

        if (metadata != null) {
            uiMessage.setMetadata(metadata);
        }

        // Convert each message part to a UIMessagePartsInner object
        List<UIMessagePartsInner> partsList = parts.stream()
            .map(ChatMessagePart::toUIMessagePart)
            .collect(Collectors.toList());
        uiMessage.setParts(partsList);

        return uiMessage;
    }

    /**
     * Create a message from a UIMessage JSON representation
     */
    public static ChatMessage fromUIMessageJson(JsonNode uiMessage, ChatThread thread) {
        if (!uiMessage.has("id") || !uiMessage.has("role") || !uiMessage.has("parts")) {
            throw new IllegalArgumentException("Invalid UI message format");
        }

        ChatMessage message = new ChatMessage();
        message.setId(UUID.fromString(uiMessage.get("id").asText()));
        message.setRole(Role.fromValue(uiMessage.get("role").asText()));
        message.setThread(thread);

        if (uiMessage.has("metadata")) {
            message.setMetadata(uiMessage.get("metadata"));
        }

        JsonNode parts = uiMessage.get("parts");
        if (parts.isArray()) {
            for (int index = 0; index < parts.size(); index++) {
                ChatMessagePart part = ChatMessagePartFactory.fromUIMessagePart(
                    message.getId(),
                    index,
                    parts.get(index)
                );
                message.addMessagePart(part);
            }
        }

        return message;
    }
}
