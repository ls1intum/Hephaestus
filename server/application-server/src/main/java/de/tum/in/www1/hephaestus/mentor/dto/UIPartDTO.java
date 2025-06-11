package de.tum.in.www1.hephaestus.mentor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface UIPartDTO {
    @NonNull String getType();
}
