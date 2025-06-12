package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.Message;
import org.springframework.lang.NonNull;

import java.util.List;

public record ChatRequestDTO(
    @NonNull String id,
    @NonNull List<Message> messages
) {
}
