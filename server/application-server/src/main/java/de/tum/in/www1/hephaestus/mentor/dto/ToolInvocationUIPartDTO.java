package de.tum.in.www1.hephaestus.mentor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolInvocationUIPartDTO(
    @NonNull String type,
    @NonNull ToolInvocationDTO toolInvocation
) implements UIPartDTO {
    
    public ToolInvocationUIPartDTO(ToolInvocationDTO toolInvocation) {
        this("tool-invocation", toolInvocation);
    }
    
    @Override
    public String getType() {
        return type;
    }
}
