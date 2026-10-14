package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CreatePracticeGroupRequestDTO(
        @JsonProperty(required = true) String slug,
        @JsonProperty(required = true) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        String icon,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        String color,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        Integer position,

        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL)
        boolean shownToDevelopers)
        implements ClosedPracticeInput {
    public CreatePracticeGroupRequestDTO {
        Objects.requireNonNull(slug);
        Objects.requireNonNull(name);
    }
}
