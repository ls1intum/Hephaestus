package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** When the throttle budget of one Slack call waits for a 429 and admits another try, and when it refuses. */
class SlackBackoffTest extends BaseUnitTest {

    private SlackRateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SlackRateLimitTracker(new SimpleMeterRegistry());
    }

    private static Stream<Arguments> rateLimitCases() {
        return Stream.of(
                // A 429 with Retry-After: 3 → the caller must back off 3000 ms (honoring Slack, not a fixed tick).
                Arguments.of(429, "3", 3000L),
                Arguments.of(429, null, 1000L),
                Arguments.of(429, "soon", 1000L),
                Arguments.of(429, "-5", 0L),
                // A hostile header saturates rather than wrapping to a negative wait.
                Arguments.of(429, String.valueOf(Long.MAX_VALUE), Long.MAX_VALUE),
                Arguments.of(429, " 99999999999999999999 ", Long.MAX_VALUE),
                // Not a number of seconds, however long: the default.
                Arguments.of(429, "99999999999999999999s", 1000L),
                Arguments.of(500, "7", SlackSendException.NOT_RATE_LIMITED));
    }

    @ParameterizedTest(name = "status={0} retryAfterHeader={1} -> {2}ms")
    @MethodSource("rateLimitCases")
    void rateLimitRetryAfterMillis(int status, @Nullable String retryAfterHeader, long expected) {
        assertThat(SlackBackoff.retryAfterMillis(status, retryAfterHeader)).isEqualTo(expected);
    }

    private static long millisSince(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    // A 429 without a wait (Retry-After: 0) waits 1000 ms plus a jitter below 250 ms.

    @Test
    void shouldAdmitAnotherTryAfterTheWholeWaitWhenItAndTheNextCallFitTheBudget() {
        SlackBackoff budget = new SlackBackoff(tracker, 7L, 2_000, 10_000, 50);
        long started = System.nanoTime();

        assertThat(budget.awaitRetry(0)).isTrue();

        assertThat(millisSince(started)).isGreaterThanOrEqualTo(1_000);
        assertThat(tracker.snapshot(7L)).isNotNull();
    }

    @Test
    void shouldRefuseAtOnceWhenTheWaitAndTheNextCallDoNotFitTheBudget() {
        SlackBackoff budget = new SlackBackoff(tracker, 7L, 2_000, 1_100, 200);
        long started = System.nanoTime();

        assertThat(budget.awaitRetry(0)).isFalse();

        assertThat(millisSince(started)).isLessThan(1_000);
        assertThat(tracker.snapshot(7L)).isNotNull();
    }

    @Test
    void shouldRefuseAtOnceWhenSlackAsksForAWaitBeyondTheCap() {
        long started = System.nanoTime();

        // Slack asks for 120 s: within the history budget, beyond its cap. The cap refuses the wait rather than
        // shortening it.
        assertThat(SlackBackoff.history(tracker, 7L).awaitRetry(120_000)).isFalse();

        assertThat(millisSince(started)).isLessThan(10_000);
        assertThat(tracker.snapshot(7L)).isNotNull();
    }

    @Test
    void shouldRefuseAtOnceAHugeRetryAfterInEitherBudget() {
        long hostile = SlackBackoff.retryAfterMillis(429, String.valueOf(Long.MAX_VALUE));
        long beyondLong = SlackBackoff.retryAfterMillis(429, "99999999999999999999");
        long started = System.nanoTime();

        assertThat(SlackBackoff.interactive(tracker, 7L).awaitRetry(hostile)).isFalse();
        assertThat(SlackBackoff.history(tracker, 7L).awaitRetry(hostile)).isFalse();
        assertThat(SlackBackoff.interactive(tracker, 7L).awaitRetry(beyondLong)).isFalse();
        assertThat(SlackBackoff.history(tracker, 7L).awaitRetry(beyondLong)).isFalse();

        assertThat(millisSince(started)).isLessThan(1_000);
        assertThat(tracker.snapshot(7L)).isNotNull();
    }

    @Test
    void shouldCountTheTimeOfTheCallsInTheBudget() {
        SlackBackoff budget = new SlackBackoff(tracker, 7L, 2_000, 1_600, 200);
        // A slow first call: the wait and the reserve (at most 1,449 ms) fit the 1,600 ms budget only if its 500 ms
        // are not counted.
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
        while (System.nanoTime() < until) {
            LockSupport.parkNanos(until - System.nanoTime());
        }

        assertThat(budget.awaitRetry(0)).isFalse();
    }

    @Test
    void shouldRefuseAnotherTryWhenTheThreadIsInterruptedWhileItWaits() {
        SlackBackoff budget = new SlackBackoff(tracker, 7L, 2_000, 10_000, 50);
        try {
            Thread.currentThread().interrupt();

            assertThat(budget.awaitRetry(0)).isFalse();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
