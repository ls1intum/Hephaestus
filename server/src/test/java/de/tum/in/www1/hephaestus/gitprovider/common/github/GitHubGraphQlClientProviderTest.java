package de.tum.in.www1.hephaestus.gitprovider.common.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRateLimit;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;

/**
 * Unit tests for {@link GitHubGraphQlClientProvider} rate limit delegation and circuit breaker.
 */
@DisplayName("GitHubGraphQlClientProvider")
class GitHubGraphQlClientProviderTest extends BaseUnitTest {

    @Mock
    private HttpGraphQlClient baseClient;

    @Mock
    private InstallationTokenProvider tokenProvider;

    @Mock
    private GitHubAppTokenService appTokens;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private RateLimitTracker rateLimitTracker;

    private GitHubGraphQlClientProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GitHubGraphQlClientProvider(
            baseClient,
            tokenProvider,
            appTokens,
            circuitBreaker,
            rateLimitTracker
        );
    }

    @Nested
    @DisplayName("Rate limit delegation")
    class RateLimitDelegation {

        @Test
        @DisplayName("trackRateLimit delegates to rateLimitTracker.updateFromResponse")
        void trackRateLimitDelegates() {
            ClientGraphQlResponse response = Mockito.mock(ClientGraphQlResponse.class);
            GHRateLimit rateLimit = new GHRateLimit();
            when(rateLimitTracker.updateFromResponse(eq(42L), eq(response))).thenReturn(rateLimit);

            GHRateLimit result = provider.trackRateLimit(42L, response);

            assertThat(result).isSameAs(rateLimit);
            verify(rateLimitTracker).updateFromResponse(42L, response);
        }

        @Test
        @DisplayName("isRateLimitCritical delegates to rateLimitTracker.isCritical")
        void isRateLimitCriticalDelegates() {
            when(rateLimitTracker.isCritical(42L)).thenReturn(true);

            assertThat(provider.isRateLimitCritical(42L)).isTrue();
            verify(rateLimitTracker).isCritical(42L);
        }

        @Test
        @DisplayName("waitIfRateLimitLow delegates to rateLimitTracker.waitIfNeeded")
        void waitIfRateLimitLowDelegates() throws InterruptedException {
            when(rateLimitTracker.waitIfNeeded(42L)).thenReturn(true);

            boolean result = provider.waitIfRateLimitLow(42L);

            assertThat(result).isTrue();
            verify(rateLimitTracker).waitIfNeeded(42L);
        }

        @Test
        @DisplayName("getRateLimitRemaining delegates to rateLimitTracker.getRemaining")
        void getRateLimitRemainingDelegates() {
            when(rateLimitTracker.getRemaining(42L)).thenReturn(500);

            assertThat(provider.getRateLimitRemaining(42L)).isEqualTo(500);
            verify(rateLimitTracker).getRemaining(42L);
        }

        @Test
        @DisplayName("getRateLimitResetAt delegates to rateLimitTracker.getResetAt")
        void getRateLimitResetAtDelegates() {
            Instant resetAt = Instant.parse("2026-01-15T10:30:00Z");
            when(rateLimitTracker.getResetAt(42L)).thenReturn(resetAt);

            assertThat(provider.getRateLimitResetAt(42L)).isEqualTo(resetAt);
            verify(rateLimitTracker).getResetAt(42L);
        }

        @Test
        @DisplayName("getRateLimitTracker returns the tracker instance")
        void getRateLimitTrackerReturnsInstance() {
            assertThat(provider.getRateLimitTracker()).isSameAs(rateLimitTracker);
        }
    }

    @Nested
    @DisplayName("Circuit breaker")
    class CircuitBreakerTests {

        @Test
        @DisplayName("isCircuitClosed returns true when state is CLOSED")
        void isCircuitClosedWhenClosed() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

            assertThat(provider.isCircuitClosed()).isTrue();
        }
    }
}
