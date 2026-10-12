package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Schema(description = "A single piece of practice feedback to read and act on")
public record PracticeStandingResultDTO(
        @NonNull @Schema(description = "Result id — handle to open the full detail")
        UUID practiceResultId,

        @NonNull @Schema(description = "The headline of the feedback")
        String title,

        @Nullable
        @Schema(
                description = "What to do — the text of the newest feedback that said something about this "
                        + "result to this developer (null if nothing was said)")
        String deliveredFeedback,

        @Nullable @Schema(description = "Impact level (null unless NOT_MET)")
        Severity severity,

        @NonNull @Schema(description = "MET or NOT_MET result supporting this standing")
        Outcome outcome,

        @NonNull @Schema(description = "The kind of reviewed work this is about")
        ArtifactKind workKind,

        @NonNull @Schema(description = "Identifier of the reviewed work")
        Long reviewedWorkId,

        @Nullable @Schema(description = "Where in the work, e.g. \"FrameRecorder.swift:212\", when known")
        String locator,

        @NonNull
        @Schema(
                description = "Why this result was recorded. BACKFILL means it came from a review of past work "
                        + "rather than from something that just happened, and nothing was posted anywhere at the time.")
        ResultOrigin origin) {
    public static PracticeStandingResultDTO from(PracticeResult practiceResult, @Nullable String deliveredFeedback) {
        return new PracticeStandingResultDTO(
                practiceResult.getId(),
                practiceResult.getSummary(),
                deliveredFeedback,
                practiceResult.getSeverity(),
                practiceResult.getOutcome(),
                practiceResult.getArtifactKind(),
                practiceResult.getArtifactId(),
                locatorOf(practiceResult.getEvidence()),
                practiceResult.getOrigin());
    }

    private static @Nullable String locatorOf(@Nullable JsonNode evidence) {
        // The first place the result rests on, its problems' included.
        PracticeResultEvidenceDTO typedEvidence = PracticeResultEvidenceDTO.from(evidence, List.of());
        if (typedEvidence == null || typedEvidence.citations().isEmpty()) {
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
