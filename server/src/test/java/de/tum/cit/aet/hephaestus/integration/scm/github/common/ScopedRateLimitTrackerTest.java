package de.tum.cit.aet.hephaestus.integration.scm.github.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHRateLimit;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;

/**
 * Unit tests for {@link ScopedRateLimitTracker}.
 */
class ScopedRateLimitTrackerTest extends BaseUnitTest {

    private ScopedRateLimitTracker tracker;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        tracker = new ScopedRateLimitTracker(meterRegistry);
    }

    @Nested
    class InitialState {

        @Test
        void shouldReturnDefaultLimitForUnknownScope() {
            assertThat(tracker.getRemaining(999L)).isEqualTo(5000);
        }

        @Test
        void shouldReturnDefaultLimitForNullScope() {
            assertThat(tracker.getRemaining(null)).isEqualTo(5000);
        }

        @Test
        void shouldReturnDefaultFromGetLimitForUnknownScope() {
            assertThat(tracker.getLimit(999L)).isEqualTo(5000);
        }

        @Test
        void shouldReturnDefaultFromGetLimitForNullScope() {
            assertThat(tracker.getLimit(null)).isEqualTo(5000);
        }

        @Test
        void shouldReturnNullResetTimeForUnknownScope() {
            assertThat(tracker.getResetAt(999L)).isNull();
        }

        @Test
        void shouldReturnNullResetTimeForNullScope() {
            assertThat(tracker.getResetAt(null)).isNull();
        }

        @Test
        void shouldHaveZeroTrackedScopes() {
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }
    }

    @Nested
    class UpdateFromResponse {

        @Test
        void shouldUpdateStateFromValidResponse() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            ClientGraphQlResponse response = mockResponseWithRateLimit(4500, 5000, 500, 2, resetTime);

            GHRateLimit result = tracker.updateFromResponse(scopeId, response);

            assertThat(result).isNotNull();
            assertThat(tracker.getRemaining(scopeId)).isEqualTo(4500);
            assertThat(tracker.getLimit(scopeId)).isEqualTo(5000);
            assertThat(tracker.getResetAt(scopeId)).isNotNull();
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(1);
        }

        @Test
        void shouldReturnNullForNullScopeId() {
            // Use a plain mock — updateFromResponse returns null before touching the response
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);

            GHRateLimit result = tracker.updateFromResponse(null, response);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForNullResponse() {
            GHRateLimit result = tracker.updateFromResponse(1L, null);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForInvalidResponse() {
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.isValid()).thenReturn(false);

            GHRateLimit result = tracker.updateFromResponse(1L, response);

            assertThat(result).isNull();
        }

        @Test
        void shouldTrackMultipleScopesIndependently() {
            Long scope1 = 1L;
            Long scope2 = 2L;

            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scope1, mockResponseWithRateLimit(4000, 5000, 1000, 3, resetTime));
            tracker.updateFromResponse(scope2, mockResponseWithRateLimit(3000, 5000, 2000, 5, resetTime));

            assertThat(tracker.getRemaining(scope1)).isEqualTo(4000);
            assertThat(tracker.getRemaining(scope2)).isEqualTo(3000);
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(2);
        }

        @Test
        void shouldUpdateExistingScopeState() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4500, 5000, 500, 2, resetTime));
            assertThat(tracker.getRemaining(scopeId)).isEqualTo(4500);

            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 3, resetTime));
            assertThat(tracker.getRemaining(scopeId)).isEqualTo(4000);
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(1);
        }
    }

    @Nested
    class IsCritical {

        @Test
        void shouldReturnTrueWhenBelowCriticalThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(50, 5000, 4950, 10, resetTime));

            assertThat(tracker.isCritical(scopeId)).isTrue();
        }

        @Test
        void shouldReturnFalseWhenAtCriticalThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(100, 5000, 4900, 10, resetTime));

            assertThat(tracker.isCritical(scopeId)).isFalse();
        }

        @Test
        void shouldReturnFalseWhenAboveCriticalThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            assertThat(tracker.isCritical(scopeId)).isFalse();
        }

        @Test
        void shouldReturnFalseForUnknownScope() {
            assertThat(tracker.isCritical(999L)).isFalse();
        }
    }

    @Nested
    class IsLow {

        @Test
        void shouldReturnTrueWhenBelowLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(300, 5000, 4700, 10, resetTime));

            assertThat(tracker.isLow(scopeId)).isTrue();
        }

        @Test
        void shouldReturnFalseWhenAtLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(500, 5000, 4500, 10, resetTime));

            assertThat(tracker.isLow(scopeId)).isFalse();
        }

        @Test
        void shouldReturnFalseWhenAboveLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            assertThat(tracker.isLow(scopeId)).isFalse();
        }

        @Test
        void shouldReturnFalseForUnknownScope() {
            assertThat(tracker.isLow(999L)).isFalse();
        }
    }

    @Nested
    class WaitIfNeeded {

        @Test
        void shouldNotWaitWhenAboveLowThreshold() throws InterruptedException {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            boolean waited = tracker.waitIfNeeded(scopeId);

            assertThat(waited).isFalse();
        }

        @Test
        void shouldNotWaitWhenBetweenCriticalAndLow() throws InterruptedException {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(200, 5000, 4800, 10, resetTime));

            boolean waited = tracker.waitIfNeeded(scopeId);

            assertThat(waited).isFalse();
        }

        @Test
        void shouldNotWaitWhenResetTimeHasPassed() throws InterruptedException {
            Long scopeId = 1L;
            OffsetDateTime pastResetTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(50, 5000, 4950, 10, pastResetTime));

            boolean waited = tracker.waitIfNeeded(scopeId);

            assertThat(waited).isFalse();
        }
    }

    @Nested
    class GetRecommendedDelay {

        @Test
        void shouldReturnZeroWhenAboveLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            Duration delay = tracker.getRecommendedDelay(scopeId);

            assertThat(delay).isEqualTo(Duration.ZERO);
        }

        @Test
        void shouldReturnZeroWhenResetTimeInPast() {
            Long scopeId = 1L;
            OffsetDateTime pastResetTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(300, 5000, 4700, 10, pastResetTime));

            Duration delay = tracker.getRecommendedDelay(scopeId);

            assertThat(delay).isEqualTo(Duration.ZERO);
        }

        @Test
        void shouldReturnPositiveDelayWhenLow() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(300, 5000, 4700, 10, resetTime));

            Duration delay = tracker.getRecommendedDelay(scopeId);

            assertThat(delay).isPositive();
        }

        @Test
        void shouldReturnZeroForUnknownScope() {
            Duration delay = tracker.getRecommendedDelay(999L);

            assertThat(delay).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    class Snapshot {

        @Test
        void shouldReturnNullForNeverObservedScope() {
            assertThat(tracker.snapshot(999L)).isNull();
        }

        @Test
        void shouldReturnNullForNullScope() {
            assertThat(tracker.snapshot(null)).isNull();
        }

        @Test
        void shouldReturnSnapshotAfterObservedResponse() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4200, 5000, 800, 3, resetTime));

            var snapshot = tracker.snapshot(scopeId);

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.limit()).isEqualTo(5000);
            assertThat(snapshot.remaining()).isEqualTo(4200);
            assertThat(snapshot.resetAt()).isEqualTo(resetTime.toInstant());
        }
    }

    @Nested
    class Metrics {

        @Test
        void shouldRegisterMetricsGaugeOnFirstUpdate() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4500, 5000, 500, 2, resetTime));

            assertThat(
                meterRegistry.find("github.graphql.ratelimit.points.remaining").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("github.graphql.ratelimit.points.limit").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("github.graphql.ratelimit.points.used").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("github.graphql.ratelimit.last_query_cost").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("github.graphql.ratelimit.seconds_until_reset").tag("scope_id", "1").gauge()
            ).isNotNull();
        }

        @Test
        void shouldReflectUpdatedValuesInGauges() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4500, 5000, 500, 2, resetTime));

            assertThat(
                meterRegistry.find("github.graphql.ratelimit.points.remaining").tag("scope_id", "1").gauge().value()
            ).isEqualTo(4500.0);
            assertThat(
                meterRegistry.find("github.graphql.ratelimit.points.limit").tag("scope_id", "1").gauge().value()
            ).isEqualTo(5000.0);
        }
    }

    // Helper Methods

    private ClientGraphQlResponse mockResponseWithRateLimit(
        int remaining,
        int limit,
        int used,
        int cost,
        OffsetDateTime resetAt
    ) {
        GHRateLimit rateLimit = new GHRateLimit(cost, limit, 0, remaining, resetAt, used);

        ClientResponseField field = mock(ClientResponseField.class);
        when(field.toEntity(GHRateLimit.class)).thenReturn(rateLimit);

        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.isValid()).thenReturn(true);
        when(response.field("rateLimit")).thenReturn(field);

        return response;
    }
}
