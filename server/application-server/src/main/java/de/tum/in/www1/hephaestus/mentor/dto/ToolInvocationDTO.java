package de.tum.in.www1.hephaestus.mentor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolInvocationDTO(
    @NonNull String state, // 'partial-call', 'call', 'result'
    Integer step,
    String toolCallId,
    String toolName,
    Object args,
    Object result
) {}
