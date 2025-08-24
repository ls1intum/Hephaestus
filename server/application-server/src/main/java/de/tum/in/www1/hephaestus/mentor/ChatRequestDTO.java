package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessage;
import java.util.UUID;
import org.springframework.lang.NonNull;

public record ChatRequestDTO(@NonNull String id, @NonNull UIMessage message, UUID previousMessageId) {}
