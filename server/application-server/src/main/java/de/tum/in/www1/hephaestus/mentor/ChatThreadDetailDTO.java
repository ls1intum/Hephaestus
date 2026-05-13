package de.tum.in.www1.hephaestus.mentor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Thread with full linear history (chronological). The webapp reconstructs the active
 * conversation path from the tree client-side; this DTO simply ships every message so a
 * refresh restores the chat view.
 */
@Schema(description = "Mentor chat thread with all messages.")
public record ChatThreadDetailDTO(
    UUID id,
    @Nullable String title,
    @Nullable UUID selectedLeafMessageId,
    Instant createdAt,
    List<ChatMessageDTO> messages
) {}
