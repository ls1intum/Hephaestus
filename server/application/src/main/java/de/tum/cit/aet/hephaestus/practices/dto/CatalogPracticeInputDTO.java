package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CatalogPracticeInputDTO(
        @JsonProperty(required = true) String slug,
        @JsonProperty(required = true) String kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        String groupSlug,

        @JsonProperty(required = true) PracticeDefinition definition,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        Integer position)
        implements ClosedPracticeInput {
    public CatalogPracticeInputDTO {
        Objects.requireNonNull(slug);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(definition);
    }
}
