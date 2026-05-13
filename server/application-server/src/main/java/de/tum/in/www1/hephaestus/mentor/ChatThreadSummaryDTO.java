package de.tum.in.www1.hephaestus.mentor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Lightweight thread row for the list endpoint — no messages. */
@Schema(description = "Mentor chat thread summary (no messages).")
public record ChatThreadSummaryDTO(UUID id, @Nullable String title, Instant createdAt) {
    public static ChatThreadSummaryDTO from(ChatThread thread) {
        return new ChatThreadSummaryDTO(thread.getId(), thread.getTitle(), thread.getCreatedAt());
    }
}
