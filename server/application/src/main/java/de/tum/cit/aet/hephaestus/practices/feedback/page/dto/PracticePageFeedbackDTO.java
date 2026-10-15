package de.tum.cit.aet.hephaestus.practices.feedback.page.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackClosedBy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticePageFeedbackDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String headline,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String body,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String nextStep,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String whyItMatters,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String whatGoodLooksLike,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticePageEvidenceDTO> evidence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant preparedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant readAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int cleanNeeded,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticePageCleanWorkDTO> cleanWork,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant closedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackClosedBy closedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackResponseDTO response,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean withdrawn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean unverifiedHistory) {
    public PracticePageFeedbackDTO {
        evidence = List.copyOf(evidence);
        cleanWork = List.copyOf(cleanWork);
    }
}
