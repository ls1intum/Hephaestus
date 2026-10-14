package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeScriptRunDTO(
        UUID jobId,
        int attempt,
        @Schema(type = "string", pattern = "^[1-9][0-9]*$") String practiceId,
        @Schema(type = "string", pattern = "^[1-9][0-9]*$") String practiceVersionId,
        ScriptRunStatus status,
        ScriptKind scriptKind,
        @Nullable ScriptStopReason stopReason,
        Instant finishedAt,
        @Nullable Long durationMs,
        @Nullable String error,
        List<PracticeScriptModelDTO> models) {
    public PracticeScriptRunDTO {
        models = List.copyOf(models);
    }
}
