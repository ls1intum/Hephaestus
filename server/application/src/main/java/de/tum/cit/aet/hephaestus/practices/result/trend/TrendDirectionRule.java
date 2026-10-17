package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;

// Failure to establish a direction does not establish equivalence; UNCERTAIN must not claim no change.
final class TrendDirectionRule {

    private TrendDirectionRule() {}

    static TrendDirection classify(
            BetaPosterior.Difference difference, double ropeHalfWidth, double credibilityThreshold) {
        if (difference.probabilityAbove(ropeHalfWidth) >= credibilityThreshold) {
            return TrendDirection.IMPROVING;
        }
        if (difference.probabilityBelow(-ropeHalfWidth) >= credibilityThreshold) {
            return TrendDirection.DECLINING;
        }
        return TrendDirection.UNCERTAIN;
    }
}
