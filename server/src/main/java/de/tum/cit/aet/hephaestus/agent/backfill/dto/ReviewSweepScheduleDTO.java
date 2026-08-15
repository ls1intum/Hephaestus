package de.tum.cit.aet.hephaestus.agent.backfill.dto;

import de.tum.cit.aet.hephaestus.agent.backfill.ReviewSweepCadence;
import de.tum.cit.aet.hephaestus.agent.backfill.ReviewSweepSchedule;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * A standing instruction to review recent work on a cadence, as an admin sees it.
 *
 * @param lookbackDays how far back each sweep looks. Bounded at write time to twice the cadence and
 *     never more than a week, which is what keeps a sweep's observations admissible in the same trend line
 *     as reviews that events triggered.
 * @param nextRunAt when the next sweep is due. Shown because it is the only way to tell a schedule that
 *     is working from one whose workspace has been skipping it.
 * @param lastRunAt not "when it last came due" — a tick that found nothing to review does not move it.
 */
public record ReviewSweepScheduleDTO(
    @NonNull UUID id,
    @NonNull ArtifactKind artifactKind,
    @NonNull ReviewSweepCadence cadence,
    @NonNull Integer lookbackDays,
    @NonNull Boolean enabled,
    @NonNull Instant nextRunAt,
    @Schema(description = "When a campaign was last opened from this schedule; absent until the first one")
    Instant lastRunAt,
    @NonNull Long createdByAccountId,
    @NonNull Instant createdAt
) {
    public static ReviewSweepScheduleDTO from(ReviewSweepSchedule schedule) {
        return new ReviewSweepScheduleDTO(
            schedule.getId(),
            schedule.kind(),
            schedule.getCadence(),
            schedule.getLookbackDays(),
            schedule.getEnabled(),
            schedule.getNextRunAt(),
            schedule.getLastRunAt(),
            schedule.getCreatedByAccountId(),
            schedule.getCreatedAt()
        );
    }
}
