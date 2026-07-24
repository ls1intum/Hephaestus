package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #1368 hardening: quartic-with-jitter backoff math. A seeded {@link RandomGenerator} makes the
 * jitter deterministic for assertions instead of asserting a range every time.
 */
class AgentJobBackoffTest extends BaseUnitTest {

    /** Always returns the midpoint (0.5) — i.e. zero jitter, exact base value. */
    private static final RandomGenerator NO_JITTER = fixed(0.5);

    /** Always returns 1.0 — the maximum +10% jitter. */
    private static final RandomGenerator MAX_POSITIVE_JITTER = fixed(1.0);

    /** Always returns 0.0 — the maximum -10% jitter. */
    private static final RandomGenerator MAX_NEGATIVE_JITTER = fixed(0.0);

    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public long nextLong() {
                return 0L;
            }
        };
    }

    @Test
    @DisplayName("attempt 1 with no jitter is base^4 + 15 = 16s")
    void attemptOneNoJitter() {
        assertThat(AgentJobBackoff.compute(1, NO_JITTER)).isEqualTo(Duration.ofSeconds(16));
    }

    @Test
    @DisplayName("attempt 0 (and negative, defensively) is treated as attempt 0: 0^4 + 15 = 15s")
    void attemptZeroOrNegativeFloorsToZero() {
        assertThat(AgentJobBackoff.compute(0, NO_JITTER)).isEqualTo(Duration.ofSeconds(15));
        assertThat(AgentJobBackoff.compute(-5, NO_JITTER)).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("attempt 3 with no jitter is 3^4 + 15 = 96s")
    void attemptThreeNoJitter() {
        assertThat(AgentJobBackoff.compute(3, NO_JITTER)).isEqualTo(Duration.ofSeconds(96));
    }

    @Test
    @DisplayName("grows monotonically with attempt number before the cap")
    void growsMonotonicallyBeforeCap() {
        Duration attempt1 = AgentJobBackoff.compute(1, NO_JITTER);
        Duration attempt2 = AgentJobBackoff.compute(2, NO_JITTER);
        Duration attempt3 = AgentJobBackoff.compute(3, NO_JITTER);

        assertThat(attempt2).isGreaterThan(attempt1);
        assertThat(attempt3).isGreaterThan(attempt2);
    }

    @Test
    @DisplayName("caps at 15 minutes regardless of how high the attempt number climbs")
    void capsAtFifteenMinutes() {
        assertThat(AgentJobBackoff.compute(100, NO_JITTER)).isEqualTo(AgentJobBackoff.CAP);
        assertThat(AgentJobBackoff.compute(1000, NO_JITTER)).isEqualTo(AgentJobBackoff.CAP);
    }

    @Test
    @DisplayName("jitter is bounded to +/-10% of the (possibly capped) base")
    void jitterIsBoundedToTenPercent() {
        Duration base = AgentJobBackoff.compute(3, NO_JITTER); // 96s
        Duration maxUp = AgentJobBackoff.compute(3, MAX_POSITIVE_JITTER);
        Duration maxDown = AgentJobBackoff.compute(3, MAX_NEGATIVE_JITTER);

        assertThat(maxUp.getSeconds()).isCloseTo(
            (long) (base.getSeconds() * 1.10),
            org.assertj.core.data.Offset.offset(1L)
        );
        assertThat(maxDown.getSeconds()).isCloseTo(
            (long) (base.getSeconds() * 0.90),
            org.assertj.core.data.Offset.offset(1L)
        );
    }

    @Test
    @DisplayName("never returns zero or negative, even at the smallest attempt with max negative jitter")
    void neverReturnsZeroOrNegative() {
        assertThat(AgentJobBackoff.compute(0, MAX_NEGATIVE_JITTER).isNegative()).isFalse();
        assertThat(AgentJobBackoff.compute(0, MAX_NEGATIVE_JITTER).isZero()).isFalse();
    }

    @Test
    @DisplayName("the real (unseeded) overload never throws and stays within [1s, cap * 1.1]")
    void realRandomOverloadStaysInBounds() {
        for (int attempt = 0; attempt <= 10; attempt++) {
            Duration d = AgentJobBackoff.compute(attempt);
            assertThat(d).isPositive();
            assertThat(d).isLessThanOrEqualTo(
                AgentJobBackoff.CAP.plusSeconds(AgentJobBackoff.CAP.toSeconds() / 10 + 1)
            );
        }
    }
}
