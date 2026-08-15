package de.tum.cit.aet.hephaestus.agent.backfill.dto;

import de.tum.cit.aet.hephaestus.agent.backfill.ReviewBackfillPauseReason;
import de.tum.cit.aet.hephaestus.agent.backfill.ReviewBackfillRun;
import de.tum.cit.aet.hephaestus.agent.backfill.ReviewBackfillStatus;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * A campaign as an admin sees it — before confirming, while it runs, and after it ends.
 *
 * @param submittedCount artifacts for which a review job was created
 * @param passedCount artifacts walked past without one: already measured at their current state, or
 *     refused by the review gate. The campaign looked at each of these and decided.
 * @param failedCount artifacts whose submission threw, leaving no job and no recorded decision. Non-zero
 *     means this campaign's baseline has holes in it, which is why it is reported apart from a pass.
 */
public record ReviewBackfillRunDTO(
    @NonNull UUID id,
    @NonNull ArtifactKind artifactKind,
    @NonNull Instant fromAt,
    @NonNull Instant toAt,
    @NonNull ReviewBackfillStatus status,
    @Schema(description = "BACKFILL for a campaign an admin scoped by hand, SWEEP for one a recurring schedule opened")
    @NonNull
    DiscoveredVia discoveredVia,
    @Schema(description = "The schedule that opened this run; absent for a campaign an admin scoped by hand")
    UUID sweepScheduleId,
    @Schema(description = "Set only while the run is PAUSED") ReviewBackfillPauseReason pauseReason,
    @NonNull Integer estimatedArtifacts,
    @Schema(description = "Forecast total spend in USD; absent when the workspace has no priced review history")
    BigDecimal estimatedCostUsd,
    @NonNull Integer submittedCount,
    @NonNull Integer passedCount,
    @NonNull Integer failedCount,
    @NonNull Long requestedByAccountId,
    @Schema(description = "Who authorised the spend; absent until the run is confirmed") Long confirmedByAccountId,
    @NonNull Instant createdAt,
    Instant startedAt,
    Instant finishedAt
) {
    public static ReviewBackfillRunDTO from(ReviewBackfillRun run) {
        return new ReviewBackfillRunDTO(
            run.getId(),
            run.kind(),
            run.getFromAt(),
            run.getToAt(),
            run.getStatus(),
            run.getDiscoveredVia(),
            run.getSweepScheduleId(),
            run.getPauseReason(),
            run.getEstimatedArtifacts(),
            run.getEstimatedCostUsd(),
            run.getSubmittedCount(),
            run.getPassedCount(),
            run.getFailedCount(),
            run.getRequestedByAccountId(),
            run.getConfirmedByAccountId(),
            run.getCreatedAt(),
            run.getStartedAt(),
            run.getFinishedAt()
        );
    }
}
