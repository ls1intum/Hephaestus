package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_LIMIT;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_OBSERVED;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_REMAINING;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_RESET;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Unit tests for {@link GitLabRateLimitTracker}.
 */
class GitLabRateLimitTrackerTest extends BaseUnitTest {

    private GitLabRateLimitTracker tracker;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        tracker = new GitLabRateLimitTracker(meterRegistry);
    }

    @Nested
    @DisplayName("Initial State")
    class InitialState {

        @Test
        @DisplayName("should return default limit (100) for unknown scope")
        void shouldReturnDefaultLimitForUnknownScope() {
            assertThat(tracker.getRemaining(999L)).isEqualTo(100);
        }

        @Test
        @DisplayName("should return default limit for null scope")
        void shouldReturnDefaultLimitForNullScope() {
            assertThat(tracker.getRemaining(null)).isEqualTo(100);
        }

        @Test
        @DisplayName("should return default from getLimit for unknown scope")
        void shouldReturnDefaultFromGetLimitForUnknownScope() {
            assertThat(tracker.getLimit(999L)).isEqualTo(100);
        }

        @Test
        @DisplayName("should return null reset time for unknown scope")
        void shouldReturnNullResetTimeForUnknownScope() {
            assertThat(tracker.getResetAt(999L)).isNull();
        }

        @Test
        @DisplayName("should have zero tracked scopes initially")
        void shouldHaveZeroTrackedScopes() {
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }
    }

    @Nested
    @DisplayName("updateFromHeaders")
    class UpdateFromHeaders {

        @Test
        @DisplayName("should update state from valid headers")
        void shouldUpdateStateFromValidHeaders() {
            Long scopeId = 1L;
            Instant resetTime = Instant.now().plusSeconds(60);
            HttpHeaders headers = createHeaders(80, 100, resetTime, 5);

            tracker.updateFromHeaders(scopeId, headers);

            assertThat(tracker.getRemaining(scopeId)).isEqualTo(80);
            assertThat(tracker.getLimit(scopeId)).isEqualTo(100);
            assertThat(tracker.getResetAt(scopeId)).isNotNull();
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle null scope ID")
        void shouldHandleNullScopeId() {
            HttpHeaders headers = createHeaders(80, 100, Instant.now().plusSeconds(60), 5);
            tracker.updateFromHeaders(null, headers);
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }

        @Test
        @DisplayName("should handle null headers")
        void shouldHandleNullHeaders() {
            tracker.updateFromHeaders(1L, null);
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }

        @Test
        @DisplayName("should handle headers without rate limit info")
        void shouldHandleEmptyHeaders() {
            tracker.updateFromHeaders(1L, new HttpHeaders());
            assertThat(tracker.getTrackedScopeCount()).isZero();
        }

        @Test
        @DisplayName("should track multiple scopes independently")
        void shouldTrackMultipleScopesIndependently() {
            Long scope1 = 1L;
            Long scope2 = 2L;
            Instant resetTime = Instant.now().plusSeconds(60);

            tracker.updateFromHeaders(scope1, createHeaders(80, 100, resetTime, 3));
            tracker.updateFromHeaders(scope2, createHeaders(50, 100, resetTime, 10));

            assertThat(tracker.getRemaining(scope1)).isEqualTo(80);
            assertThat(tracker.getRemaining(scope2)).isEqualTo(50);
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should update existing scope state")
        void shouldUpdateExistingScopeState() {
            Long scopeId = 1L;
            Instant resetTime = Instant.now().plusSeconds(60);

            tracker.updateFromHeaders(scopeId, createHeaders(80, 100, resetTime, 3));
            assertThat(tracker.getRemaining(scopeId)).isEqualTo(80);

            tracker.updateFromHeaders(scopeId, createHeaders(60, 100, resetTime, 5));
            assertThat(tracker.getRemaining(scopeId)).isEqualTo(60);
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("isCritical")
    class IsCritical {

        @Test
        @DisplayName("should return true when remaining below critical threshold (5)")
        void shouldReturnTrueWhenBelowCriticalThreshold() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(3, 100, Instant.now().plusSeconds(60), 10));
            assertThat(tracker.isCritical(scopeId)).isTrue();
        }

        @Test
        @DisplayName("should return false when remaining at critical threshold")
        void shouldReturnFalseWhenAtCriticalThreshold() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(5, 100, Instant.now().plusSeconds(60), 10));
            assertThat(tracker.isCritical(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false when remaining above critical threshold")
        void shouldReturnFalseWhenAboveCriticalThreshold() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(80, 100, Instant.now().plusSeconds(60), 5));
            assertThat(tracker.isCritical(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown scope (defaults to 100)")
        void shouldReturnFalseForUnknownScope() {
            assertThat(tracker.isCritical(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("isLow")
    class IsLow {

        @Test
        @DisplayName("should return true when remaining below low threshold (15)")
        void shouldReturnTrueWhenBelowLowThreshold() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(10, 100, Instant.now().plusSeconds(60), 5));
            assertThat(tracker.isLow(scopeId)).isTrue();
        }

        @Test
        @DisplayName("should return false when remaining at low threshold")
        void shouldReturnFalseWhenAtLowThreshold() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(15, 100, Instant.now().plusSeconds(60), 5));
            assertThat(tracker.isLow(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown scope (defaults to 100)")
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
            tracker.updateFromHeaders(scopeId, createHeaders(80, 100, Instant.now().plusSeconds(60), 5));
            assertThat(tracker.waitIfNeeded(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should not wait when between critical and low thresholds")
        void shouldNotWaitWhenBetweenCriticalAndLow() throws InterruptedException {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(10, 100, Instant.now().plusSeconds(60), 5));
            assertThat(tracker.waitIfNeeded(scopeId)).isFalse();
        }

        @Test
        @DisplayName("should not wait when reset time has already passed")
        void shouldNotWaitWhenResetTimeHasPassed() throws InterruptedException {
            Long scopeId = 1L;
            Instant pastResetTime = Instant.now().minusSeconds(30);
            tracker.updateFromHeaders(scopeId, createHeaders(3, 100, pastResetTime, 10));
            assertThat(tracker.waitIfNeeded(scopeId)).isFalse();
        }
    }

    @Nested
    @DisplayName("getRecommendedDelay")
    class GetRecommendedDelay {

        @Test
        @DisplayName("should return zero when remaining is above low threshold")
        void shouldReturnZeroWhenAboveLowThreshold() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(80, 100, Instant.now().plusSeconds(60), 5));
            assertThat(tracker.getRecommendedDelay(scopeId)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should return zero when reset time is in the past")
        void shouldReturnZeroWhenResetTimeInPast() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(10, 100, Instant.now().minusSeconds(30), 5));
            assertThat(tracker.getRecommendedDelay(scopeId)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should return positive delay when remaining is low")
        void shouldReturnPositiveDelayWhenLow() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(10, 100, Instant.now().plusSeconds(30), 5));
            assertThat(tracker.getRecommendedDelay(scopeId)).isPositive();
        }

        @Test
        @DisplayName("should return zero for unknown scope")
        void shouldReturnZeroForUnknownScope() {
            assertThat(tracker.getRecommendedDelay(999L)).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should register metrics on first update")
        void shouldRegisterMetricsOnFirstUpdate() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(80, 100, Instant.now().plusSeconds(60), 5));

            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.points.remaining").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.points.limit").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.points.used").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.last_query_cost").tag("scope_id", "1").gauge()
            ).isNotNull();
            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.seconds_until_reset").tag("scope_id", "1").gauge()
            ).isNotNull();
        }

        @Test
        @DisplayName("should reflect updated values in gauges")
        void shouldReflectUpdatedValuesInGauges() {
            Long scopeId = 1L;
            tracker.updateFromHeaders(scopeId, createHeaders(80, 100, Instant.now().plusSeconds(60), 5));

            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.points.remaining").tag("scope_id", "1").gauge().value()
            ).isEqualTo(80.0);
            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.points.limit").tag("scope_id", "1").gauge().value()
            ).isEqualTo(100.0);
            assertThat(
                meterRegistry.find("gitlab.graphql.ratelimit.points.used").tag("scope_id", "1").gauge().value()
            ).isEqualTo(20.0);
        }
    }

    @Nested
    @DisplayName("Eviction")
    class Eviction {

        @Test
        @DisplayName("should not evict recently updated entries")
        void shouldNotEvictRecentEntries() {
            tracker.updateFromHeaders(1L, createHeaders(80, 100, Instant.now().plusSeconds(60), 5));
            tracker.evictStaleEntries();
            assertThat(tracker.getTrackedScopeCount()).isEqualTo(1);
        }
    }

    // ========== Helper Methods ==========

    private HttpHeaders createHeaders(int remaining, int limit, Instant resetAt, int observed) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_RATE_LIMIT_REMAINING, String.valueOf(remaining));
        headers.set(HEADER_RATE_LIMIT_LIMIT, String.valueOf(limit));
        headers.set(HEADER_RATE_LIMIT_RESET, String.valueOf(resetAt.getEpochSecond()));
        headers.set(HEADER_RATE_LIMIT_OBSERVED, String.valueOf(observed));
        return headers;
    }
}
