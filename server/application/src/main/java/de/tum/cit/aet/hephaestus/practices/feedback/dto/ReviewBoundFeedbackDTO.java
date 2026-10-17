package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewBoundFeedbackDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackDestination destination,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackState state,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant deliveredAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean withdrawn) {}
