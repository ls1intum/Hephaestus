package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link IntegrationNatsConsumer}.
 *
 * <p>Focus is on the small set of pure helpers carved out of the connect / dispatch path:
 *
 * <ul>
 *   <li>{@link IntegrationNatsConsumer#reconnectBackoffMs(int)} — full-jitter
 *       exponential backoff used by the connect-with-retry loop. Bounds + monotonicity
 *       under attempt growth are nailed down so future tuning doesn't accidentally drop
 *       sub-second delays under load.</li>
 * </ul>
 *
 * <p>The orchestrator itself is exercised indirectly by the workspace-lifecycle tests
 * ({@code GitLabWorkspaceInitializationServiceTest}) and the JetStream loop is covered
 * by the existing {@code IntegrationPoisonHandlerTest} +
 * {@code IntegrationMessageDispatcherTest}. A full Spring-context test would re-test
 * the JetStream client, which is out of scope here.
 */
@Tag("unit")
class IntegrationNatsConsumerTest {

    @Nested
    class ReconnectBackoffMs {

        @ParameterizedTest(name = "attempt={0} → delay in [{1}, {2}] ms")
        @CsvSource(
            {
                // attempt, minMs (= base * 2^attempt, no jitter floor), maxMs (= base * 2^attempt + JITTER_MAX, capped at 30_000)
                "0, 1000, 2000",
                "1, 2000, 3000",
                "2, 4000, 5000",
                "3, 8000, 9000",
                "4, 16000, 17000",
                "5, 30000, 30000",
                "6, 30000, 30000",
            }
        )
        void clampedExponentialWithJitter(int attempt, long minExpected, long maxExpected) {
            long delay = IntegrationNatsConsumer.reconnectBackoffMs(attempt);
            assertThat(delay)
                .as("attempt=%d expected to land in [%d, %d] ms (got %d)", attempt, minExpected, maxExpected, delay)
                .isGreaterThanOrEqualTo(minExpected)
                .isLessThanOrEqualTo(maxExpected);
        }

        @Test
        void negativeAttemptClampedToZero() {
            long delay = IntegrationNatsConsumer.reconnectBackoffMs(-5);
            // base=1000ms, jitter up to 1000ms → [1000, 2000]
            assertThat(delay).isBetween(1_000L, 2_000L);
        }

        @Test
        void delayClampedToHardMax() {
            // Try the same call many times to stress the random-jitter path.
            for (int i = 0; i < 1000; i++) {
                long delay = IntegrationNatsConsumer.reconnectBackoffMs(100);
                assertThat(delay).isLessThanOrEqualTo(30_000L);
            }
        }
    }
}
