package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * List-view DTO for practice results. Omits large text fields (delivered feedback, evidence rationale)
 * and internal fields (agentJobId, occurrenceKey, evidence) to keep payloads small. Each problem appears only as
 * its severity and one line; feedback text never appears in a list row.
 */
@Schema(description = "Practice result summary for list views")
public record PracticeResultListDTO(
        @NonNull @Schema(description = "Result ID") UUID id,
        @NonNull @Schema(description = "Practice slug") String practiceSlug,
        @NonNull @Schema(description = "Practice name") String practiceName,

        @NonNull @Schema(description = "Artifact type (e.g. PULL_REQUEST)")
        ArtifactKind artifactKind,

        @NonNull @Schema(description = "Artifact entity ID") Long artifactId,

        @NonNull @Schema(description = "Result summary") String summary,

        @NonNull @Schema(description = "Result of the practice review")
        Outcome outcome,

        @Nullable @Schema(description = "Severity level (null unless outcome is NOT_MET)")
        Severity severity,

        @NonNull ReviewClaimCurrentness claimCurrentness,

        @NonNull @Schema(description = "What occasioned the measurement; never mix origins in one trend line")
        ResultOrigin origin,

        @NonNull @Schema(description = "When the result was made")
        Instant observedAt,

        @NonNull
        @Schema(
                description = "The problems of a NOT_MET result in display order, as the result detail shows them; "
                        + "empty for any other outcome and when the detail would show no evidence")
        List<ObservationSummaryDTO> problems) {

    /**
     * Maps a {@link PracticeResult} entity (with eagerly fetched practice) to a list DTO. {@code problems} are the
     * result's own problems in display order, or empty when the result detail would withhold its evidence.
     */
    public static PracticeResultListDTO from(PracticeResult practiceResult, List<Observation> problems) {
        var practice = practiceResult.getPractice();
        return new PracticeResultListDTO(
                practiceResult.getId(),
                practice.getSlug(),
                practice.getName(),
                practiceResult.getArtifactKind(),
                practiceResult.getArtifactId(),
                practiceResult.getSummary(),
                practiceResult.getOutcome(),
                practiceResult.getSeverity(),
                ReviewClaimCurrentness.of(
                        practiceResult.getPracticeRevision(), practice, practiceResult.getSupersededAt()),
                practiceResult.getOrigin(),
                practiceResult.getObservedAt(),
                problems.stream().map(ObservationSummaryDTO::from).toList());
    }
}
