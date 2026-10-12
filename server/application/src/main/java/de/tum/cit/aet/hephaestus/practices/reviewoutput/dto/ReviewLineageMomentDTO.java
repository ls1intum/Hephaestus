package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewTrigger;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

@Schema(description = "One moment on the reviewed work, such as opened, pushed or merged")
public record ReviewLineageMomentDTO(
        @NonNull @Schema(description = "What happened, in the words a review trigger uses")
        ReviewTrigger kind,

        @NonNull @Schema(description = "When it happened upstream")
        Instant occurredAt,

        @Schema(description = "Whether this moment started the review", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean startedReview,

        @Schema(
                description = "Whether this moment came after the one that started the review and was folded into it,"
                        + " so it started no review of its own",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean joinedReview) {}
