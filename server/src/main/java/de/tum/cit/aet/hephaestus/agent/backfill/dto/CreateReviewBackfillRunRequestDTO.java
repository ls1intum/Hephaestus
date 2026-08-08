package de.tum.cit.aet.hephaestus.agent.backfill.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Ask for a campaign to be enumerated and costed. Creates nothing that spends money: the run comes back
 * awaiting confirmation, and only a subsequent status change starts it.
 *
 * @param artifactKind the kind of work to review — one kind per campaign, so the count and the cost on
 *     the confirmation screen mean one thing
 * @param fromAt window start, inclusive, over the artifact's creation time
 * @param toAt window end, exclusive
 */
// jspecify's @NonNull states intent to static analysis; jakarta's @NotNull is what @Valid enforces at
// the request boundary. Both, because a missing field must be a 400 that names the field, not an NPE
// deep in the service surfacing as an undocumented 500.
public record CreateReviewBackfillRunRequestDTO(
    @NonNull
    @NotNull
    @Schema(
        description = "Kind of work to review",
        example = "scm.pull_request",
        allowableValues = { "scm.pull_request", "scm.issue" }
    )
    ArtifactKind artifactKind,
    @NonNull
    @NotNull
    @Schema(description = "Window start, inclusive, over the artifact's creation time")
    Instant fromAt,
    @NonNull @NotNull @Schema(description = "Window end, exclusive") Instant toAt
) {}
