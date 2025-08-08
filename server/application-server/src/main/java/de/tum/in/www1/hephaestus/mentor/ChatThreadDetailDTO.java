package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for chat thread with full message content.
 * Used for initializing useChat in the frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatThreadDetailDTO {

    /**
     * Unique identifier for the thread
     */
    private UUID id;

    /**
     * Thread title (may be null for untitled threads)
     */
    private String title;

    /**
     * When the thread was created
     */
    private Instant createdAt;

    /**
     * All messages in the conversation path (as JSON objects for useChat initialization)
     * As exception, we do not use a DTO here since we included those intelligece-service models in the OpenAPI spec.
     */
    private List<UIMessage> messages;

    /**
     * ID of the currently selected leaf message (end of active conversation path)
     */
    private UUID selectedLeafMessageId;
}
