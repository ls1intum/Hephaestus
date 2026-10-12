package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramKind;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Where a result came from, as admission pinned it from server data: the job and attempt, the practice revision and
 * the program it held, and the revision the capture pinned. For admins and evaluation; never shown to the developer.
 */
@Schema(description = "Where a practice result came from")
public record PracticeResultProvenanceDTO(
        @NonNull UUID jobId,
        @Schema(requiredMode = RequiredMode.REQUIRED) int attempt,

        @Schema(description = "The practice revision the result was measured against") @Nullable
        Long practiceRevisionId,

        @NonNull PracticeProgramKind programKind,

        @Schema(description = "SHA-256 of the practice's own script; null for the built-in review") @Nullable
        String programDigest,

        @Schema(description = "The head commit the capture pinned; null for work without one") @Nullable
        String capturedRevision) {

    public static @Nullable PracticeResultProvenanceDTO from(@Nullable JsonNode provenance) {
        if (provenance == null || !provenance.isObject()) return null;
        String job = provenance.path("jobId").asString(null);
        String kind = provenance.path("program").path("kind").asString(null);
        if (job == null || kind == null) return null;
        JsonNode revision = provenance.path("practiceRevisionId");
        try {
            return new PracticeResultProvenanceDTO(
                    UUID.fromString(job),
                    provenance.path("attempt").asInt(),
                    revision.isIntegralNumber() ? revision.asLong() : null,
                    PracticeProgramKind.valueOf(kind.toUpperCase(Locale.ROOT)),
                    provenance.path("program").path("digest").asString(null),
                    provenance.path("capturedRevision").asString(null));
        } catch (IllegalArgumentException unreadable) {
            // Provenance the server did not write reads as unknown, never as a failed page.
            return null;
        }
    }
}
