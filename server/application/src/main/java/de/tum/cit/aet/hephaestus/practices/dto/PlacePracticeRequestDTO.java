package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PlacePracticeRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        String groupSlug,

        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL)
        int position)
        implements ClosedPracticeInput {}
