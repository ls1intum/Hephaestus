package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "An observation in a lineage")
public record ReviewLineageObservationDTO(
        @NonNull UUID id,

        @NonNull @Schema(description = "The review that recorded it")
        UUID agentJobId,

        @Schema(description = "Observation text; absent when source access cannot be verified") @Nullable
        String summary,

        @NonNull String practiceSlug,
        @NonNull String practiceName,
        @NonNull Outcome outcome,

        @Schema(description = "Severity band (null unless outcome is NOT_MET)") @Nullable
        Severity severity,

        @NonNull Instant observedAt,

        @Schema(
                description =
                        "Whether the request names this observation, or the feedback it names was written from it",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean focus,

        @Schema(
                description = "Whether a workspace admin marked it incorrect and has not restored it",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean invalidated,

        @Schema(
                description = "How many pieces of feedback were written from it, by any review",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int feedbackCount,

        @Schema(description = "When a workspace admin marked it incorrect; null unless it is marked incorrect now")
        @Nullable
        Instant markedIncorrectAt,

        @Schema(
                description = "How many pieces of feedback the review that recorded it wrote from it; a later review"
                        + " may write more, which feedbackCount counts too",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int ownFeedbackCount) {}
