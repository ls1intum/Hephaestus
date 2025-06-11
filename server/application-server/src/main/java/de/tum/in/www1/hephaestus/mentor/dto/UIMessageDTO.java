package de.tum.in.www1.hephaestus.mentor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UIMessageDTO(
    @NonNull String id,
    OffsetDateTime createdAt,
    @NonNull String content,
    @NonNull String role, // 'system' | 'user' | 'assistant' 
    List<UIPartDTO> parts
) {}
