package de.tum.cit.aet.hephaestus.practices.result.trend;

import java.util.Arrays;

final class BetaPosterior {

    static final int GRID_SIZE = 256;
    private static final double JEFFREYS_PRIOR = 0.5;

    private final double alpha;
    private final double beta;

    private BetaPosterior(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    static BetaPosterior from(int opportunities, double metShareSum) {
        if (opportunities < 0 || metShareSum < 0 || metShareSum > opportunities) {
            throw new IllegalArgumentException("Invalid opportunity evidence");
        }
        return new BetaPosterior(JEFFREYS_PRIOR + metShareSum, JEFFREYS_PRIOR + opportunities - metShareSum);
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

    // Jeffreys priors can diverge at grid edges; evaluate midpoints.
    // The beta normalizer cancels during normalization and cannot change the output.
    private double[] gridMass(int gridSize) {
        double[] logs = new double[gridSize];
        double max = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < gridSize; index++) {
            double point = (index + 0.5) / gridSize;
            logs[index] = (alpha - 1.0) * Math.log(point) + (beta - 1.0) * Math.log1p(-point);
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

    @SuppressWarnings("ArrayRecordComponent") // numeric grids for integration; never compared, hashed or printed
    record Difference(double mean, double variance, double[] currentMass, double[] previousMass, int gridSize) {
        double probabilityAbove(double boundary) {
            return probabilityWhere(boundary, true);
        }

        double probabilityBelow(double boundary) {
            return probabilityWhere(boundary, false);
        }

        // The boundary moves monotonically, so prefix sums replace the quadratic product without changing edge
        // comparisons.
        private double probabilityWhere(double boundary, boolean above) {
            double[] previousPrefix = new double[gridSize + 1];
            for (int previous = 0; previous < gridSize; previous++) {
                previousPrefix[previous + 1] = previousPrefix[previous] + previousMass[previous];
            }
            double probability = 0.0;
            int edge = 0;
            for (int current = 0; current < gridSize; current++) {
                double threshold = ((current + 0.5) / gridSize) - boundary;
                if (above) {
                    // current - previous > boundary  ⟺  previous < threshold
                    while (edge < gridSize && ((edge + 0.5) / gridSize) < threshold) {
                        edge++;
                    }
                    probability += currentMass[current] * previousPrefix[edge];
                } else {
                    // current - previous < boundary  ⟺  previous > threshold
                    while (edge < gridSize && ((edge + 0.5) / gridSize) <= threshold) {
                        edge++;
                    }
                    probability += currentMass[current] * (1.0 - previousPrefix[edge]);
                }
            }
            return probability;
        }
    }
}
