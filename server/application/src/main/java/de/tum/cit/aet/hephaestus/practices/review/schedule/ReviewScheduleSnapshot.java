package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCadence;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record ReviewScheduleSnapshot(
        String itemKind,
        ReviewCadence cadence,
        @Nullable Integer lookbackDays,
        boolean enabled,
        @Nullable BigDecimal budgetUsd)
        implements ConfigAuditSnapshot {
    static ReviewScheduleSnapshot of(ReviewSchedule s) {
        return new ReviewScheduleSnapshot(
                s.getKind(), s.getCadence(), s.getLookbackDays(), s.isEnabled(), s.getBudgetUsd());
    }
}
