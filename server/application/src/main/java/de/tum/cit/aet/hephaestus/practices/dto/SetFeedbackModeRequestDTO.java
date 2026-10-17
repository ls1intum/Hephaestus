package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SetFeedbackModeRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @JsonProperty(required = true) @Nullable
        FeedbackMode feedbackMode)
        implements ClosedPracticeInput {}
