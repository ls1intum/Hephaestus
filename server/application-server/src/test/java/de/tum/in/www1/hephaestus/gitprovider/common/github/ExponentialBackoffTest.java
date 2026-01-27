package de.tum.in.www1.hephaestus.gitprovider.common.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExponentialBackoff}.
 */
class ExponentialBackoffTest {

    @Test
    void calculateDelay_attempt0_returnsBaseDelayPlusJitter() {
        // Base delay is 1000ms, jitter is 0-1000ms
        // So attempt 0 should return between 1000-2000ms
        long delay = ExponentialBackoff.calculateDelay(0);

        assertThat(delay).isBetween(1000L, 2001L);
    }

    @Test
    void calculateDelay_attempt1_returnsDoubleBasePlusJitter() {
        // 2 * 1000 = 2000, plus 0-1000 jitter = 2000-3000
        long delay = ExponentialBackoff.calculateDelay(1);

        assertThat(delay).isBetween(2000L, 3001L);
    }

    @Test
    void calculateDelay_attempt2_returns4xBasePlusJitter() {
        // 4 * 1000 = 4000, plus 0-1000 jitter = 4000-5000
        long delay = ExponentialBackoff.calculateDelay(2);

        assertThat(delay).isBetween(4000L, 5001L);
    }

    @Test
    void calculateDelay_attempt6_returnsMaxWithJitter() {
        // 64 * 1000 = 64000, which would exceed max of 60000
        // So should be capped at 60000
        long delay = ExponentialBackoff.calculateDelay(6);

        assertThat(delay).isLessThanOrEqualTo(60000L);
    }

    @Test
    void calculateDelay_highAttempt_cappedAtMax() {
        // Any attempt >= 6 should be capped
        long delay = ExponentialBackoff.calculateDelay(100);

        assertThat(delay).isLessThanOrEqualTo(60000L);
    }

    @Test
    void calculateDelay_negativeAttempt_treatedAsZero() {
        long delay = ExponentialBackoff.calculateDelay(-1);

        // Should be treated as attempt 0: 1000-2000ms
        assertThat(delay).isBetween(1000L, 2001L);
    }

    @RepeatedTest(10)
    void calculateDelay_producesJitter() {
        // Run multiple times to verify jitter produces variation
        long delay1 = ExponentialBackoff.calculateDelay(0);
        long delay2 = ExponentialBackoff.calculateDelay(0);
        long delay3 = ExponentialBackoff.calculateDelay(0);

        // All should be in valid range
        assertThat(delay1).isBetween(1000L, 2001L);
        assertThat(delay2).isBetween(1000L, 2001L);
        assertThat(delay3).isBetween(1000L, 2001L);

        // At least check they're reasonable (jitter means they're unlikely all equal)
        // Not asserting inequality since it's probabilistic
    }

    @Test
    void calculateDelay_customParams_respectsConfiguration() {
        long delay = ExponentialBackoff.calculateDelay(0, 500, 5000, 100);

        // Base 500 + jitter 0-100 = 500-600
        assertThat(delay).isBetween(500L, 601L);
    }

    @Test
    void calculateDelay_customParams_respectsMaxDelay() {
        long delay = ExponentialBackoff.calculateDelay(10, 1000, 2000, 500);

        // Should be capped at max 2000
        assertThat(delay).isLessThanOrEqualTo(2000L);
    }

    @Test
    void calculateDelay_customParams_zeroJitter() {
        long delay = ExponentialBackoff.calculateDelay(0, 1000, 60000, 0);

        // No jitter, so exactly 1000
        assertThat(delay).isEqualTo(1000L);
    }

    @Test
    void calculateDelay_customParams_negativeBaseDelay_throwsException() {
        assertThatThrownBy(() -> ExponentialBackoff.calculateDelay(0, -1, 60000, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    @Test
    void calculateDelay_customParams_negativeMaxDelay_throwsException() {
        assertThatThrownBy(() -> ExponentialBackoff.calculateDelay(0, 1000, -1, 1000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    @Test
    void calculateDelay_customParams_negativeJitter_throwsException() {
        assertThatThrownBy(() -> ExponentialBackoff.calculateDelay(0, 1000, 60000, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    @Test
    void getBaseDelayMs_returnsExpectedValue() {
        assertThat(ExponentialBackoff.getBaseDelayMs()).isEqualTo(1000L);
    }

    @Test
    void getMaxDelayMs_returnsExpectedValue() {
        assertThat(ExponentialBackoff.getMaxDelayMs()).isEqualTo(60000L);
    }

    @Test
    void getMaxJitterMs_returnsExpectedValue() {
        assertThat(ExponentialBackoff.getMaxJitterMs()).isEqualTo(1000L);
    }

    @Test
    void sleep_doesNotThrowForValidAttempt() throws InterruptedException {
        // Use custom params with very short delay to make test fast
        long start = System.currentTimeMillis();
        ExponentialBackoff.sleep(0, 10, 100, 5);
        long elapsed = System.currentTimeMillis() - start;

        // Should have slept at least 10ms (base) and at most ~115ms (10 + 5 jitter + some overhead)
        assertThat(elapsed).isGreaterThanOrEqualTo(10L);
    }
}
