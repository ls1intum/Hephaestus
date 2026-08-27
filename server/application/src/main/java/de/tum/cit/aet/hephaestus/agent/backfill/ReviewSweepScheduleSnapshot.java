package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a sweep schedule.
 *
 * <p>The terms, not the bookkeeping: cadence, window, kind and whether it is on. {@code nextRunAt} moves
 * on every tick and would turn an append-only trail of decisions into a log of the clock.
 */
record ReviewSweepScheduleSnapshot(
        String artifactKind,
        ReviewSweepCadence cadence,
        Integer lookbackDays,
        @Nullable Boolean enabled) implements ConfigAuditSnapshot {
    static ReviewSweepScheduleSnapshot of(ReviewSweepSchedule schedule) {
        return new ReviewSweepScheduleSnapshot(
                schedule.getArtifactKind(), schedule.getCadence(), schedule.getLookbackDays(), schedule.getEnabled());
    }
}
