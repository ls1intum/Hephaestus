package de.tum.cit.aet.hephaestus.practices.observation.trend;

import java.util.Arrays;

/**
 * Deterministic beta-posterior maths for continuously recomputed opportunity-indexed trends.
 *
 * <p>The estimator is Bayesian deliberately: repository evidence arrives in bursts and the surface is
 * recomputed after every new opportunity. Bayesian posterior inference remains valid under optional stopping;
 * replacing this with a repeatedly peeked frequentist two-sample test would inflate false positives. See the
 * anytime-valid comparison literature cited in {@code practice-trend-display-spec.md} §0.
 */
final class BetaPosterior {

    static final int GRID_SIZE = 256;
    private static final double JEFFREYS_PRIOR = 0.5;
    private static final double[] LANCZOS = {
        676.5203681218851,
        -1259.1392167224028,
        771.32342877765313,
        -176.61502916214059,
        12.507343278686905,
        -0.13857109526572012,
        9.9843695780195716e-6,
        1.5056327351493116e-7,
    };

    private final double alpha;
    private final double beta;

    private BetaPosterior(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    static BetaPosterior from(int opportunities, double positiveShareSum) {
        if (opportunities < 0 || positiveShareSum < 0 || positiveShareSum > opportunities) {
            throw new IllegalArgumentException("Invalid opportunity evidence");
        }
        return new BetaPosterior(JEFFREYS_PRIOR + positiveShareSum, JEFFREYS_PRIOR + opportunities - positiveShareSum);
    }

    double mean() {
        return alpha / (alpha + beta);
    }

    double variance() {
        double sum = alpha + beta;
        return (alpha * beta) / (sum * sum * (sum + 1.0));
    }

    Difference differenceFrom(BetaPosterior previous) {
        return differenceFrom(previous, GRID_SIZE);
    }

    Difference differenceFrom(BetaPosterior previous, int gridSize) {
        double[] currentMass = gridMass(gridSize);
        double[] previousMass = previous.gridMass(gridSize);
        double currentMean = gridMean(currentMass, gridSize);
        double previousMean = gridMean(previousMass, gridSize);
        double variance =
            gridVariance(currentMass, currentMean, gridSize) + gridVariance(previousMass, previousMean, gridSize);
        return new Difference(currentMean - previousMean, variance, currentMass, previousMass, gridSize);
    }

    private static double gridMean(double[] mass, int gridSize) {
        double mean = 0.0;
        for (int index = 0; index < gridSize; index++) {
            mean += ((index + 0.5) / gridSize) * mass[index];
        }
        return mean;
    }

    private static double gridVariance(double[] mass, double mean, int gridSize) {
        double variance = 0.0;
        for (int index = 0; index < gridSize; index++) {
            double deviation = ((index + 0.5) / gridSize) - mean;
            variance += deviation * deviation * mass[index];
        }
        return variance;
    }

    private double[] gridMass(int gridSize) {
        double[] logs = new double[gridSize];
        double max = Double.NEGATIVE_INFINITY;
        double logNormalizer = logGamma(alpha) + logGamma(beta) - logGamma(alpha + beta);
        for (int index = 0; index < gridSize; index++) {
            double point = (index + 0.5) / gridSize;
            logs[index] = (alpha - 1.0) * Math.log(point) + (beta - 1.0) * Math.log1p(-point) - logNormalizer;
            max = Math.max(max, logs[index]);
        }
        double[] mass = new double[gridSize];
        double sum = 0.0;
        for (int index = 0; index < gridSize; index++) {
            mass[index] = Math.exp(logs[index] - max);
            sum += mass[index];
        }
        final double total = sum;
        Arrays.setAll(mass, index -> mass[index] / total);
        return mass;
    }

    private static double logGamma(double value) {
        if (value < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * value)) - logGamma(1.0 - value);
        }
        double shifted = value - 1.0;
        double series = 0.99999999999980993;
        for (int index = 0; index < LANCZOS.length; index++) {
            series += LANCZOS[index] / (shifted + index + 1.0);
        }
        double t = shifted + LANCZOS.length - 0.5;
        return 0.5 * Math.log(2.0 * Math.PI) + (shifted + 0.5) * Math.log(t) - t + Math.log(series);
    }

    record Difference(double mean, double variance, double[] currentMass, double[] previousMass, int gridSize) {
        double probabilityAbove(double boundary) {
            return probabilityWhere(boundary, true);
        }

        double probabilityBelow(double boundary) {
            return probabilityWhere(boundary, false);
        }

        double probabilityInside(double halfWidth) {
            return Math.max(0.0, 1.0 - probabilityAbove(halfWidth) - probabilityBelow(-halfWidth));
        }

        private double probabilityWhere(double boundary, boolean above) {
            double probability = 0.0;
            for (int current = 0; current < gridSize; current++) {
                double currentPoint = (current + 0.5) / gridSize;
                for (int previous = 0; previous < gridSize; previous++) {
                    double previousPoint = (previous + 0.5) / gridSize;
                    double delta = currentPoint - previousPoint;
                    if (above ? delta > boundary : delta < boundary) {
                        probability += currentMass[current] * previousMass[previous];
                    }
                }
            }
            return probability;
        }
    }
}
