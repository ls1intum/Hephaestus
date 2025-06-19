package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.intelligenceservice.model.UIMessage;
import java.util.List;
import org.springframework.lang.NonNull;

public record ChatRequestDTO(@NonNull String id, @NonNull List<UIMessage> messages) {}
