package de.tum.cit.aet.hephaestus.practices.observation.trend;

/**
 * Applies a practical-equivalence band to posterior change probabilities.
 *
 * <p>Only a direction is claimed. There is deliberately no verdict for "nothing is changing": establishing
 * equivalence is a stronger claim than establishing difference, and at four opportunities per bundle the
 * posterior mass inside the band peaks around 0.70 — it can never clear the credibility threshold, so such a
 * verdict would either never fire or need a band wide enough to swallow real progress. {@code UNCERTAIN}
 * carries that case honestly: no direction is supported, which is not the same as asserting there is none.
 */
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
        return TrendDirection.UNCERTAIN;
    }
}
