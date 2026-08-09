package de.tum.cit.aet.hephaestus.agent.backfill.dto;

import de.tum.cit.aet.hephaestus.agent.backfill.ReviewSweepCadence;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Create a standing instruction to sweep this workspace's recent work.
 *
 * <p>Unlike a backfill campaign there is no separate confirmation step: this request <em>is</em> the
 * authorisation to spend on the cadence it names, and it is recorded against the account that made it.
 *
 * <p>There is deliberately no repository or author list. Which repositories are reviewed is the
 * workspace's review scope and whose work is reviewed is the practice-review role; both already apply to
 * every review this workspace runs, a sweep included.
 *
 * @param lookbackDays how far back each sweep reaches. At most twice the cadence, and never more than
 *     seven days — past that the corpus is one somebody chose in hindsight, which is a backfill campaign
 *     and records itself as one.
 */
public record CreateReviewSweepScheduleRequestDTO(
    @NonNull
    @NotNull
    @Schema(
        description = "Kind of work to sweep",
        example = "scm.pull_request",
        allowableValues = { "scm.pull_request", "scm.issue" }
    )
    ArtifactKind artifactKind,
    @NonNull @NotNull @Schema(description = "How often the sweep runs") ReviewSweepCadence cadence,
    @NonNull
    @NotNull
    @Min(1)
    @Max(7)
    @Schema(description = "How far back each sweep looks, in days", example = "2")
    Integer lookbackDays
) {}
