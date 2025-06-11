package de.tum.in.www1.hephaestus.mentor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextUIPartDTO(
    @NonNull String type,
    @NonNull String text
) implements UIPartDTO {
    
    public TextUIPartDTO(String text) {
        this("text", text);
    }
    
    @Override
    public String getType() {
        return type;
    }
}
