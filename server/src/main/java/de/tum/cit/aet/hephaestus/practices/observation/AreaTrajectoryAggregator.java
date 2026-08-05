package de.tum.cit.aet.hephaestus.practices.observation;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Aggregates comparable practice deltas into one optionally weighted area signal. */
final class AreaTrajectoryAggregator {

    static final double DEFAULT_WEIGHT = 1.0;

    private AreaTrajectoryAggregator() {}

    /**
     * Practices without a previous snapshot are excluded rather than treated as steady. Missing weights use
     * {@value #DEFAULT_WEIGHT}; zero weight deliberately removes a practice from the aggregate, preparing the
     * calculation for a later admin-configurable importance field without coupling it to persistence now.
     */
    static @Nullable AreaTrajectorySignal aggregate(
        Collection<PracticeTrajectory> trajectories,
        Map<String, Double> weightsByPractice
    ) {
        double weightedDelta = 0.0;
        double totalWeight = 0.0;
        int evidenceCount = 0;
        int practiceCount = 0;
        for (PracticeTrajectory trajectory : trajectories) {
            if (!trajectory.hasComparison()) {
                continue;
            }
            double weight = weightsByPractice.getOrDefault(trajectory.practiceSlug(), DEFAULT_WEIGHT);
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Practice trajectory weight must be finite and non-negative");
            }
            if (weight == 0.0) {
                continue;
            }
            weightedDelta += trajectory.scoreDelta() * weight;
            totalWeight += weight;
            evidenceCount += trajectory.currentEvidenceCount() + trajectory.previousEvidenceCount();
            practiceCount++;
        }
        if (totalWeight == 0.0) {
            return null;
        }
        double areaDelta = weightedDelta / totalWeight;
        return new AreaTrajectorySignal(AreaTrajectory.fromDelta(areaDelta), areaDelta, evidenceCount, practiceCount);
    }

    record AreaTrajectorySignal(AreaTrajectory direction, double scoreDelta, int evidenceCount, int practiceCount) {}
}
