package de.tum.in.www1.hephaestus.activity.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for XpPrecision utility class.
 *
 * <p>Verifies that XP rounding is consistent and uses HALF_UP mode.
 */
@DisplayName("XpPrecision")
class XpPrecisionTest extends BaseUnitTest {

    @Nested
    @DisplayName("round(double)")
    class RoundDouble {

        @Test
        @DisplayName("rounds to 2 decimal places")
        void roundsToTwoDecimalPlaces() {
            assertThat(XpPrecision.round(1.234)).isEqualTo(1.23);
            assertThat(XpPrecision.round(1.239)).isEqualTo(1.24);
        }

        @Test
        @DisplayName("uses HALF_UP rounding mode")
        void usesHalfUpRounding() {
            // 0.5 rounds up
            assertThat(XpPrecision.round(1.125)).isEqualTo(1.13);
            assertThat(XpPrecision.round(1.124)).isEqualTo(1.12);
        }

        @Test
        @DisplayName("preserves values with 2 or fewer decimal places")
        void preservesShortDecimals() {
            assertThat(XpPrecision.round(1.5)).isEqualTo(1.5);
            assertThat(XpPrecision.round(1.25)).isEqualTo(1.25);
            assertThat(XpPrecision.round(1.0)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("handles zero")
        void handlesZero() {
            assertThat(XpPrecision.round(0.0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("handles large values")
        void handlesLargeValues() {
            assertThat(XpPrecision.round(999.999)).isEqualTo(1000.0);
            assertThat(XpPrecision.round(1000.004)).isEqualTo(1000.0);
        }
    }

    @Nested
    @DisplayName("roundToInt(double)")
    class RoundToIntDouble {

        @ParameterizedTest(name = "{0} rounds to {1}")
        @CsvSource(
            {
                "0.0, 0",
                "0.4, 0",
                "0.5, 1",
                "0.6, 1",
                "1.0, 1",
                "1.4, 1",
                "1.5, 2",
                "99.4, 99",
                "99.5, 100",
                "99.9, 100",
            }
        )
        @DisplayName("uses HALF_UP rounding for integer conversion")
        void usesHalfUpForIntegers(double input, int expected) {
            assertThat(XpPrecision.roundToInt(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("handles large aggregated values")
        void handlesLargeAggregatedValues() {
            assertThat(XpPrecision.roundToInt(10000.5)).isEqualTo(10001);
            assertThat(XpPrecision.roundToInt(99999.4)).isEqualTo(99999);
        }
    }

    @Nested
    @DisplayName("roundToInt(Double)")
    class RoundToIntDoubleObject {

        @Test
        @DisplayName("handles null as zero")
        void handlesNullAsZero() {
            assertThat(XpPrecision.roundToInt((Double) null)).isEqualTo(0);
        }

        @Test
        @DisplayName("rounds non-null values")
        void roundsNonNullValues() {
            assertThat(XpPrecision.roundToInt(Double.valueOf(99.5))).isEqualTo(100);
            assertThat(XpPrecision.roundToInt(Double.valueOf(99.4))).isEqualTo(99);
        }
    }

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("DECIMAL_PLACES is 2")
        void decimalPlacesIsTwo() {
            assertThat(XpPrecision.DECIMAL_PLACES).isEqualTo(2);
        }

        @Test
        @DisplayName("ROUNDING_MODE is HALF_UP")
        void roundingModeIsHalfUp() {
            assertThat(XpPrecision.ROUNDING_MODE).isEqualTo(RoundingMode.HALF_UP);
        }
    }

    @Nested
    @DisplayName("Fairness scenarios")
    class FairnessScenarios {

        @Test
        @DisplayName("users with 99.9 vs 99.1 XP get different integer scores")
        void differentXpGetsDifferentScores() {
            // This was the bug - truncation gave both users 99
            int userA = XpPrecision.roundToInt(99.9);
            int userB = XpPrecision.roundToInt(99.1);

            assertThat(userA).isEqualTo(100);
            assertThat(userB).isEqualTo(99);
            assertThat(userA).isGreaterThan(userB);
        }

        @Test
        @DisplayName("users with 50.5 vs 50.4 XP get correctly rounded scores")
        void edgeCaseAtHalf() {
            int userA = XpPrecision.roundToInt(50.5);
            int userB = XpPrecision.roundToInt(50.4);

            assertThat(userA).isEqualTo(51);
            assertThat(userB).isEqualTo(50);
        }

        @Test
        @DisplayName("aggregating many small XP values maintains precision")
        void aggregationMaintainsPrecision() {
            // Simulate summing 10,000 events of 0.5 XP each
            // This is the classic floating-point concern: 0.5 can't be represented exactly in IEEE 754
            // But since we round before storage, each value is clean
            double sum = 0.0;
            double xpPerEvent = XpPrecision.round(0.5); // Pre-rounded as in production
            int eventCount = 10_000;

            for (int i = 0; i < eventCount; i++) {
                sum += xpPerEvent;
            }

            // Expected: 10,000 * 0.5 = 5000.0
            // With naive doubles, this could drift
            // With pre-rounded values, it stays exact
            int finalScore = XpPrecision.roundToInt(sum);
            assertThat(finalScore).isEqualTo(5000);
        }

        @Test
        @DisplayName("classic 0.1 + 0.2 problem is avoided by pre-rounding")
        void classicFloatingPointProblemAvoided() {
            // Without rounding: 0.1 + 0.2 = 0.30000000000000004
            // With rounding: each value is clean before storage

            double val1 = XpPrecision.round(0.1);
            double val2 = XpPrecision.round(0.2);
            double sum = XpPrecision.round(val1 + val2);

            assertThat(sum).isEqualTo(0.3);
        }

        @Test
        @DisplayName("leaderboard ranking is stable with maximum practical XP")
        void leaderboardStableAtMaxXp() {
            // Two users with very close scores at high XP levels
            double userA = 1_000_000.45;
            double userB = 1_000_000.44;

            int scoreA = XpPrecision.roundToInt(userA);
            int scoreB = XpPrecision.roundToInt(userB);

            // Both round to 1,000,000 - this is expected and fair
            // The 0.01 difference is sub-integer precision
            assertThat(scoreA).isEqualTo(1_000_000);
            assertThat(scoreB).isEqualTo(1_000_000);

            // But 0.5 difference would create ranking distinction
            double userC = 1_000_000.50;
            int scoreC = XpPrecision.roundToInt(userC);
            assertThat(scoreC).isEqualTo(1_000_001);
            assertThat(scoreC).isGreaterThan(scoreA);
        }
    }
}
