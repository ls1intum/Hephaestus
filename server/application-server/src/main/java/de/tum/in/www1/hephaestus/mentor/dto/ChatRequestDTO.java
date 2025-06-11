package de.tum.in.www1.hephaestus.mentor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatRequestDTO(
    @NonNull List<Map<String, Object>> messages
) {}
