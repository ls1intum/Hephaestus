package de.tum.cit.aet.hephaestus.integration.outline.client;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Outline emits rate-limit headers only from its 429 catch block, so these tests pin the two things that
 * follow: a healthy instance reports nothing, and what a 429 reports is parsed as Outline actually sends it.
 */
@Tag("unit")
class OutlineRateLimitTrackerTest extends BaseUnitTest {

    private static final String SCOPE = "https://outline.example.test";

    private OutlineRateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new OutlineRateLimitTracker(new SimpleMeterRegistry());
    }

    @Nested
    class NothingObserved {

        /**
         * The steady state for a working Outline: successful responses carry no rate-limit headers at all,
         * so there is no fact to report and the row must stay off the page.
         */
        @Test
        void successfulResponseWithoutHeaders_createsNoState() {
            tracker.updateFromHeaders(SCOPE, new HttpHeaders());

            assertThat(tracker.snapshot(SCOPE)).isNull();
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }

        @Test
        void nullScopeOrHeaders_createNoState() {
            tracker.updateFromHeaders(null, new HttpHeaders());
            tracker.updateFromHeaders(SCOPE, null);

            assertThat(tracker.snapshot(SCOPE)).isNull();
        }

        @Test
        void neverObservedScope_hasNoSnapshot() {
            assertThat(tracker.snapshot("https://other.example.test")).isNull();
            assertThat(tracker.snapshot(null)).isNull();
        }
    }

    @Nested
    class ThrottleObservations {

        /**
         * Outline's middleware writes {@code Retry-After} as {@code msBeforeNext / 1000} without flooring,
         * so fractional seconds are the norm, not an edge case. Rounding up is the safe direction.
         */
        @Test
        void fractionalRetryAfter_isParsedAndRoundedUp() {
            Instant before = Instant.now();

            tracker.updateFromHeaders(SCOPE, throttleHeaders("1.5", "1000", "0"));

            RateLimitSnapshot snapshot = tracker.snapshot(SCOPE);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.throttledUntil()).isNotNull();
            // ceil(1.5) == 2 seconds from the observation
            assertThat(snapshot.throttledUntil()).isBetween(before.plusSeconds(2), Instant.now().plusSeconds(2));
        }

        @Test
        void integralRetryAfter_isParsed() {
            tracker.updateFromHeaders(SCOPE, throttleHeaders("60", "1000", "0"));

            RateLimitSnapshot snapshot = tracker.snapshot(SCOPE);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.throttledUntil()).isAfter(Instant.now().plusSeconds(50));
        }

        /**
         * {@code RateLimit-Reset} is a JavaScript {@code Date.toString()} — neither epoch-seconds nor
         * ISO-8601. It must not blow up, and it must not become the reset instant; {@code Retry-After} is
         * the reliable signal and the tracker derives the window end from it.
         */
        @Test
        void javascriptDateResetHeader_isIgnoredWithoutThrowing() {
            HttpHeaders headers = throttleHeaders("2", "1000", "0");
            headers.set("RateLimit-Reset", "Sat Jul 18 2026 10:15:00 GMT+0000 (Coordinated Universal Time)");

            tracker.updateFromHeaders(SCOPE, headers);

            RateLimitSnapshot snapshot = tracker.snapshot(SCOPE);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.resetAt()).isEqualTo(snapshot.throttledUntil());
        }

        @Test
        void observedCountsAreReportedVerbatimWhileTheWindowIsOpen() {
            tracker.updateFromHeaders(SCOPE, throttleHeaders("30", "1000", "0"));

            RateLimitSnapshot snapshot = tracker.snapshot(SCOPE);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.limit()).isEqualTo(1000);
            assertThat(snapshot.remaining()).isZero();
            assertThat(snapshot.observedAt()).isNotNull();
        }

        /**
         * The frozen-exhaustion bug: a 429's {@code remaining: 0} is true for an instant, not forever.
         * Once the window lapses it must stop being reported — while the configured ceiling, which is
         * window-invariant, survives.
         */
        @Test
        void closedWindow_retiresRemainingButKeepsTheCeiling() {
            // A Retry-After of 0 means the window is already over by the time we read the snapshot.
            tracker.updateFromHeaders(SCOPE, throttleHeaders("0", "1000", "0"));

            RateLimitSnapshot snapshot = tracker.snapshot(SCOPE);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.remaining()).isNull();
            assertThat(snapshot.resetAt()).isNull();
            assertThat(snapshot.limit()).isEqualTo(1000);
        }

        /** The old {@code 0/0} seed claimed an exhaustion nobody measured. It must be unreachable. */
        @Test
        void headerlessScopeNeverRendersZeroOfZero() {
            tracker.updateFromHeaders(SCOPE, new HttpHeaders());

            assertThat(tracker.snapshot(SCOPE)).isNull();
        }

        /**
         * The {@code 0/0} seed's live escape hatch: a throttle whose count headers were stripped (a proxy,
         * a partial middleware) used to create state and then report "0 remaining of 0" — an exhaustion
         * claim assembled entirely from constructor defaults. The back-off is a fact; the counts are not.
         */
        @Test
        void throttleWithoutCountHeaders_reportsTheBackoffButNoCounts() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(OutlineRateLimitTracker.HEADER_RETRY_AFTER, "30");

            tracker.updateFromHeaders(SCOPE, headers);

            RateLimitSnapshot snapshot = tracker.snapshot(SCOPE);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.throttledUntil()).isAfter(Instant.now().plusSeconds(20));
            assertThat(snapshot.limit()).isNull();
            assertThat(snapshot.remaining()).isNull();
        }

        @Test
        void unparseableCountHeader_recordsNothingRatherThanAGuess() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(OutlineRateLimitTracker.HEADER_LIMIT, "not-a-number");

            tracker.updateFromHeaders(SCOPE, headers);

            assertThat(tracker.snapshot(SCOPE)).isNull();
        }
    }

    private static HttpHeaders throttleHeaders(String retryAfter, String limit, String remaining) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(OutlineRateLimitTracker.HEADER_RETRY_AFTER, retryAfter);
        headers.set(OutlineRateLimitTracker.HEADER_LIMIT, limit);
        headers.set(OutlineRateLimitTracker.HEADER_REMAINING, remaining);
        return headers;
    }
}
