package de.tum.cit.aet.hephaestus.practices.observation.trend;

/** Applies a practical-equivalence band to posterior change probabilities. */
final class TrendDirectionRule {

    private TrendDirectionRule() {}

    static TrendDirection classify(
        BetaPosterior.Difference difference,
        double ropeHalfWidth,
        double credibilityThreshold
    ) {
        if (difference.probabilityAbove(ropeHalfWidth) >= credibilityThreshold) {
            return TrendDirection.IMPROVING;
        }
        if (difference.probabilityBelow(-ropeHalfWidth) >= credibilityThreshold) {
            return TrendDirection.DECLINING;
        }
        if (difference.probabilityInside(ropeHalfWidth) >= credibilityThreshold) {
            return TrendDirection.STABLE;
        }
        return TrendDirection.UNCERTAIN;
    }
}
