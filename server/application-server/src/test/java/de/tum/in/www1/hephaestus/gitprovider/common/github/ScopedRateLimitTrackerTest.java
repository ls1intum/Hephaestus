package de.tum.in.www1.hephaestus.gitprovider.common.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRateLimit;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("should return default limit for unknown scope")
        void shouldReturnDefaultLimitForUnknownScope() {
            assertThat(tracker.getRemaining(999L)).isEqualTo(5000);
        }

        @Test
        @DisplayName("should return default limit for null scope")
        void shouldReturnDefaultLimitForNullScope() {
            assertThat(tracker.getRemaining(null)).isEqualTo(5000);
        }

        @Test
        @DisplayName("should return default limit from getLimit for unknown scope")
        void shouldReturnDefaultFromGetLimitForUnknownScope() {
            assertThat(tracker.getLimit(999L)).isEqualTo(5000);
        }

        @Test
        @DisplayName("should return default limit from getLimit for null scope")
        void shouldReturnDefaultFromGetLimitForNullScope() {
            assertThat(tracker.getLimit(null)).isEqualTo(5000);
        }

        @Test
        @DisplayName("should return null reset time for unknown scope")
        void shouldReturnNullResetTimeForUnknownScope() {
            assertThat(tracker.getResetAt(999L)).isNull();
        }

        @Test
        @DisplayName("should return null reset time for null scope")
        void shouldReturnNullResetTimeForNullScope() {
            assertThat(tracker.getResetAt(null)).isNull();
        }

        @Test
        @DisplayName("should have zero tracked scopes initially")
        void shouldHaveZeroTrackedScopes() {
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }
    }

    @Nested
    @DisplayName("updateFromResponse")
    class UpdateFromResponse {

        @Test
        @DisplayName("should update state from valid response")
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
        @DisplayName("should return null for null scope ID")
        void shouldReturnNullForNullScopeId() {
            // Use a plain mock — updateFromResponse returns null before touching the response
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);

            GHRateLimit result = tracker.updateFromResponse(null, response);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null response")
        void shouldReturnNullForNullResponse() {
            GHRateLimit result = tracker.updateFromResponse(1L, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for invalid response")
        void shouldReturnNullForInvalidResponse() {
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.isValid()).thenReturn(false);

            GHRateLimit result = tracker.updateFromResponse(1L, response);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should track multiple scopes independently")
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
        @DisplayName("should update existing scope state")
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
    @DisplayName("isCritical")
    class IsCritical {

        @Test
        @DisplayName("should return true when remaining below critical threshold")
        void shouldReturnTrueWhenBelowCriticalThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(50, 5000, 4950, 10, resetTime));

            assertThat(tracker.isCritical(scopeId)).isTrue();
        }

        @Test
        @DisplayName("should return false when remaining at critical threshold")
        void shouldReturnFalseWhenAtCriticalThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(100, 5000, 4900, 10, resetTime));

            assertThat(tracker.isCritical(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false when remaining above critical threshold")
        void shouldReturnFalseWhenAboveCriticalThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            assertThat(tracker.isCritical(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown scope (defaults to 5000)")
        void shouldReturnFalseForUnknownScope() {
            assertThat(tracker.isCritical(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("isLow")
    class IsLow {

        @Test
        @DisplayName("should return true when remaining below low threshold")
        void shouldReturnTrueWhenBelowLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(300, 5000, 4700, 10, resetTime));

            assertThat(tracker.isLow(scopeId)).isTrue();
        }

        @Test
        @DisplayName("should return false when remaining at low threshold")
        void shouldReturnFalseWhenAtLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(500, 5000, 4500, 10, resetTime));

            assertThat(tracker.isLow(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false when remaining above low threshold")
        void shouldReturnFalseWhenAboveLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            assertThat(tracker.isLow(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown scope (defaults to 5000)")
        void shouldReturnFalseForUnknownScope() {
            assertThat(tracker.isLow(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("waitIfNeeded")
    class WaitIfNeeded {

        @Test
        @DisplayName("should not wait when remaining is above low threshold")
        void shouldNotWaitWhenAboveLowThreshold() throws InterruptedException {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            boolean waited = tracker.waitIfNeeded(scopeId);

            assertThat(waited).isFalse();
        }

        @Test
        @DisplayName("should not wait when remaining is between critical and low thresholds")
        void shouldNotWaitWhenBetweenCriticalAndLow() throws InterruptedException {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(200, 5000, 4800, 10, resetTime));

            boolean waited = tracker.waitIfNeeded(scopeId);

            assertThat(waited).isFalse();
        }

        @Test
        @DisplayName("should not wait when reset time has already passed")
        void shouldNotWaitWhenResetTimeHasPassed() throws InterruptedException {
            Long scopeId = 1L;
            OffsetDateTime pastResetTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(50, 5000, 4950, 10, pastResetTime));

            boolean waited = tracker.waitIfNeeded(scopeId);

            assertThat(waited).isFalse();
        }
    }

    @Nested
    @DisplayName("getRecommendedDelay")
    class GetRecommendedDelay {

        @Test
        @DisplayName("should return zero when remaining is above low threshold")
        void shouldReturnZeroWhenAboveLowThreshold() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(4000, 5000, 1000, 5, resetTime));

            Duration delay = tracker.getRecommendedDelay(scopeId);

            assertThat(delay).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should return zero when reset time is in the past")
        void shouldReturnZeroWhenResetTimeInPast() {
            Long scopeId = 1L;
            OffsetDateTime pastResetTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(300, 5000, 4700, 10, pastResetTime));

            Duration delay = tracker.getRecommendedDelay(scopeId);

            assertThat(delay).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should return positive delay when remaining is low")
        void shouldReturnPositiveDelayWhenLow() {
            Long scopeId = 1L;
            OffsetDateTime resetTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
            tracker.updateFromResponse(scopeId, mockResponseWithRateLimit(300, 5000, 4700, 10, resetTime));

            Duration delay = tracker.getRecommendedDelay(scopeId);

            assertThat(delay).isPositive();
        }

        @Test
        @DisplayName("should return zero for unknown scope")
        void shouldReturnZeroForUnknownScope() {
            Duration delay = tracker.getRecommendedDelay(999L);

            assertThat(delay).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should register metrics gauge on first update")
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
        @DisplayName("should reflect updated values in gauges")
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

    // ========== Helper Methods ==========

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
