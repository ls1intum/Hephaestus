package de.tum.cit.aet.hephaestus.practices.observation.trend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class BetaPosteriorTest {

    @Test
    void shouldExposeKnownBetaMoments() {
        BetaPosterior posterior = BetaPosterior.from(4, 3.0);

        assertThat(posterior.mean()).isEqualTo(3.5 / 5.0);
        assertThat(posterior.variance()).isEqualTo((3.5 * 1.5) / (25.0 * 6.0));
    }

    @Test
    void shouldClaimNoDirectionForEquivalentBundles() {
        // Even 80 opportunities a side, identical in every respect, yield no direction — the rule only ever
        // claims movement, never its absence. At the four-per-bundle size this surface runs on, the mass
        // inside the practical-equivalence band peaks near 0.70, so a "nothing is changing" verdict could not
        // have fired anyway without a band wide enough to swallow real progress.
        BetaPosterior.Difference difference = BetaPosterior.from(80, 40).differenceFrom(BetaPosterior.from(80, 40));

        assertThat(TrendDirectionRule.classify(difference, 0.15, 0.90)).isEqualTo(TrendDirection.UNCERTAIN);
    }

    @Test
    void shouldClassifyClearlySeparatedBundlesAsImproving() {
        BetaPosterior.Difference difference = BetaPosterior.from(12, 11).differenceFrom(BetaPosterior.from(12, 1));

        assertThat(TrendDirectionRule.classify(difference, 0.15, 0.90)).isEqualTo(TrendDirection.IMPROVING);
    }

    @Test
    void shouldKeepClearLookingSmallSampleSplitUncertain() {
        BetaPosterior.Difference difference = BetaPosterior.from(1, 1).differenceFrom(BetaPosterior.from(1, 0));

        assertThat(TrendDirectionRule.classify(difference, 0.15, 0.90)).isEqualTo(TrendDirection.UNCERTAIN);
    }

    @Test
    void shouldDeriveDifferenceMomentsFromTheDeterministicGrid() {
        BetaPosterior.Difference difference = BetaPosterior.from(1, 1).differenceFrom(BetaPosterior.from(1, 0), 2);

        assertThat(difference.mean()).isCloseTo(0.25, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(difference.variance()).isCloseTo(0.09375, org.assertj.core.data.Offset.offset(1.0e-12));
    }

    /**
     * Two identical bundles have a difference distribution that is symmetric about zero, whatever the
     * evidence behind them. That fixes three probabilities in closed form — half the mass above zero, and
     * the two tails equal — which is what pins the grid convolution to the maths it stands for rather than
     * to whatever it happens to compute today.
     */
    @Test
    void shouldMatchTheClosedFormForASymmetricDifference() {
        BetaPosterior.Difference difference = BetaPosterior.from(9, 6.0).differenceFrom(BetaPosterior.from(9, 6.0));

        assertThat(difference.mean()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(difference.probabilityAbove(0.0)).isCloseTo(
            difference.probabilityBelow(0.0),
            org.assertj.core.data.Offset.offset(1.0e-12)
        );
        assertThat(difference.probabilityAbove(0.15)).isCloseTo(
            difference.probabilityBelow(-0.15),
            org.assertj.core.data.Offset.offset(1.0e-12)
        );
        // The two tails are disjoint, so they never account for more than all of the mass. What is left over
        // is the practical-equivalence band — the region the rule deliberately makes no claim about, which is
        // why nothing here reads it.
        double tails = difference.probabilityAbove(0.15) + difference.probabilityBelow(-0.15);
        assertThat(tails).isBetween(0.0, 1.0);
        assertThat(1.0 - tails).isGreaterThan(0.0);
        // The tie mass is the discretisation gap against the continuous 0.5; it stays small and, being
        // excluded from both tails, lands in the practical-equivalence band.
        assertThat(1.0 - difference.probabilityAbove(0.0) - difference.probabilityBelow(0.0)).isLessThan(0.01);
    }

    @Test
    void shouldConvergeAtTheSpecifiedGridSize() {
        BetaPosterior current = BetaPosterior.from(7, 5.5);
        BetaPosterior previous = BetaPosterior.from(7, 2.5);

        double at256 = current.differenceFrom(previous, 256).probabilityAbove(0.15);
        double at1024 = current.differenceFrom(previous, 1024).probabilityAbove(0.15);

        assertThat(at256).isCloseTo(at1024, org.assertj.core.data.Offset.offset(1.0e-3));
    }
}
