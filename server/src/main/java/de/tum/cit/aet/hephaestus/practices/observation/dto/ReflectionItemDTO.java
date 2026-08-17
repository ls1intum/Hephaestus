package de.tum.cit.aet.hephaestus.practices.observation.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Schema(description = "A single piece of practice feedback to read and act on")
public record ReflectionItemDTO(
    @NonNull @Schema(description = "Observation id — handle to open the full detail") UUID observationId,
    @NonNull @Schema(description = "The headline of the feedback") String title,
    @Nullable
    @Schema(description = "What to do — the delivered feedback for this observation (null if nothing was delivered)")
    String deliveredFeedback,
    @Nullable @Schema(description = "Impact level (null unless assessed BAD)") Severity severity,
    @NonNull @Schema(description = "The kind of work this is about (PR / issue)") ArtifactKind artifactKind,
    @NonNull @Schema(description = "Id of the PR / issue this is about") Long artifactId,
    @Nullable @Schema(description = "Where in the work, e.g. \"FrameRecorder.swift:212\", when known") String locator,
    @NonNull
    @Schema(
        description = "What occasioned the measurement. BACKFILL means it came from a review of past work " +
            "rather than from something that just happened, and nothing was posted anywhere at the time."
    )
    ObservationOrigin origin
) {
    public static ReflectionItemDTO from(Observation observation, @Nullable String deliveredFeedback) {
        return new ReflectionItemDTO(
            observation.getId(),
            observation.getSummary(),
            deliveredFeedback,
            observation.getSeverity(),
            observation.getArtifactKind(),
            observation.getArtifactId(),
            locatorOf(observation.getEvidence()),
            observation.getOrigin()
        );
    }

    private static @Nullable String locatorOf(@Nullable JsonNode evidence) {
        ObservationEvidenceDTO typedEvidence = ObservationEvidenceDTO.from(evidence);
        if (typedEvidence == null) {
            return null;
        }
        EvidenceCitationDTO citation = typedEvidence.citations().getFirst();
        if (!isCodeSource(citation.sourceKind())) {
            return null;
        }
        return citation.path() + ":" + citation.startLine();
    }

    private static boolean isCodeSource(String sourceKind) {
        return sourceKind.equals("scm.pull-request.diff") || sourceKind.equals("scm.repository.tree");
    }
}
