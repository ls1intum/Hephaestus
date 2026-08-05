package de.tum.cit.aet.hephaestus.practices.observation;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Change in one practice between its two most-recent evidence-bearing UTC days. The score delta is
 * normalized for work volume before it reaches this record; evidence counts remain separate so direction
 * and confidence are not conflated.
 */
public record PracticeTrajectory(
    String practiceSlug,
    @Nullable AreaTrajectory direction,
    double scoreDelta,
    int currentEvidenceCount,
    int previousEvidenceCount,
    LocalDate currentAsOf,
    @Nullable LocalDate previousAsOf
) {
    public boolean hasComparison() {
        return previousAsOf != null && direction != null;
    }
}
