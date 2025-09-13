package de.tum.in.www1.hephaestus.mentor;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

/**
 * DTO for chat thread summary information.
 * Used for listing threads without loading full message content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatThreadSummaryDTO {

    /**
     * Unique identifier for the thread
     */
    @NonNull
    private UUID id;

    /**
     * Thread title (may be null for untitled threads)
     */
    @NonNull
    private String title;

    /**
     * When the thread was created
     */
    @NonNull
    private Instant createdAt;
}
