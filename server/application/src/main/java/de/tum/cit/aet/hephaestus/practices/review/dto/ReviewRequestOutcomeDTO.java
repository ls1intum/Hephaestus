package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewRequestOutcomeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewRequestStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID reviewId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewRequestReason reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reasonDescription) {}
