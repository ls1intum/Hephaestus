package de.tum.cit.aet.hephaestus.practices.feedback.inapp.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * One piece of work that carries the habit a in-app message is about.
 *
 * <p>The unit of proof at the process level is recurrence, so evidence here is a <em>set of
 * artifacts</em>, never a quoted line. A quoted line is task-level proof and it already appeared on the
 * work itself; repeating it would make the practice pages a second copy of the pull-request comment
 * instead of the thing the comment cannot say.
 *
 * <p>No severity, no confidence, no assessment: those are properties of the measurement, and a surface
 * that showed them would invite the reader to treat a count of them as a score.
 */
@Schema(description = "One piece of work the pattern was observed on")
public record InAppEvidenceDTO(
    @NonNull @Schema(description = "Kind of work, e.g. scm.pull_request") String artifactKind,
    @NonNull @Schema(description = "Identifier of the work within its kind") Long artifactId,
    @NonNull @Schema(description = "When the measurement behind this occurrence was taken") Instant observedAt,
    @Schema(description = "What the review recorded on this piece of work") String summary
) {
    public static InAppEvidenceDTO from(Observation observation) {
        ArtifactKind kind = observation.getArtifactKind();
        return new InAppEvidenceDTO(
            kind == null ? null : kind.value(),
            observation.getArtifactId(),
            observation.getObservedAt(),
            observation.getSummary()
        );
    }
}
