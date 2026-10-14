package de.tum.cit.aet.hephaestus.practices.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackCopyDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackCopyKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String body,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String path,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer startLine,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer endLine,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        CitationSide side,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackCopyState state,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant writeStartedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String externalUrl,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String externalRef,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID chatMessageId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant postedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        FeedbackStopReason stopReason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String lastError) {

    public static FeedbackCopyDTO from(FeedbackCopy copy) {
        return new FeedbackCopyDTO(
                copy.getId(),
                copy.getKind(),
                copy.getPosition(),
                copy.getBody(),
                copy.getPath(),
                copy.getStartLine(),
                copy.getEndLine(),
                copy.getSide(),
                copy.getState(),
                copy.getWriteStartedAt(),
                copy.getExternalUrl(),
                copy.getExternalRef(),
                copy.getChatMessageId(),
                copy.getPostedAt(),
                copy.getStopReason(),
                copy.getLastError());
    }
}
