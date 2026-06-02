package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.exception.CircuitBreakerOpenException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;

/**
 * Unit tests for {@link GitLabGraphQlClientProvider}.
 */
@Tag("unit")
class GitLabGraphQlClientProviderTest extends BaseUnitTest {

    @Mock
    private HttpGraphQlClient baseClient;

    @Mock
    private GitLabTokenService tokenService;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private GitLabRateLimitTracker rateLimitTracker;

    private GitLabGraphQlClientProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GitLabGraphQlClientProvider(baseClient, tokenService, circuitBreaker, rateLimitTracker);
    }

    @Nested
    class ForScope {

        @Test
        @SuppressWarnings("unchecked")
        void shouldBuildClientWithCorrectUrlAndAuth() {
            when(tokenService.getAccessToken(1L)).thenReturn("glpat-test-token");
            when(tokenService.resolveServerUrl(1L)).thenReturn("https://gitlab.example.com");

            HttpGraphQlClient.Builder<?> mutateBuilder = mock(HttpGraphQlClient.Builder.class);
            HttpGraphQlClient builtClient = mock(HttpGraphQlClient.class);

            // Use doReturn to avoid wildcard capture issues with self-referential generics
            doReturn(mutateBuilder).when(baseClient).mutate();
            doReturn(mutateBuilder).when(mutateBuilder).url("https://gitlab.example.com/api/graphql");
            doReturn(mutateBuilder).when(mutateBuilder).header("Authorization", "Bearer glpat-test-token");
            doReturn(mutateBuilder).when(mutateBuilder).webClient(any());
            doReturn(builtClient).when(mutateBuilder).build();

            HttpGraphQlClient result = provider.forScope(1L);

            assertThat(result).isSameAs(builtClient);
            verify(mutateBuilder).url("https://gitlab.example.com/api/graphql");
            verify(mutateBuilder).header("Authorization", "Bearer glpat-test-token");
            verify(mutateBuilder).webClient(any());
        }

        @Test
        void shouldThrowWhenTokenServiceFails() {
            when(tokenService.getAccessToken(1L)).thenThrow(new IllegalStateException("Scope 1 is not active"));

            assertThatThrownBy(() -> provider.forScope(1L)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class WithToken {

        @Test
        @SuppressWarnings("unchecked")
        void shouldBuildClientWithProvidedTokenAndUrl() {
            HttpGraphQlClient.Builder<?> mutateBuilder = mock(HttpGraphQlClient.Builder.class);
            HttpGraphQlClient builtClient = mock(HttpGraphQlClient.class);

            doReturn(mutateBuilder).when(baseClient).mutate();
            doReturn(mutateBuilder).when(mutateBuilder).url("https://gitlab.com/api/graphql");
            doReturn(mutateBuilder).when(mutateBuilder).header("Authorization", "Bearer glpat-direct");
            doReturn(builtClient).when(mutateBuilder).build();

            HttpGraphQlClient result = provider.withToken("glpat-direct", "https://gitlab.com");

            assertThat(result).isSameAs(builtClient);
        }
    }

    @Nested
    class CircuitBreakerTests {

        @Test
        void isCircuitClosedWhenClosed() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
            assertThat(provider.isCircuitClosed()).isTrue();
        }

        @Test
        void isCircuitClosedReturnsFalseWhenOpen() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
            assertThat(provider.isCircuitClosed()).isFalse();
        }

        @Test
        void acquirePermissionThrowsWhenOpen() {
            CallNotPermittedException exception = mock(CallNotPermittedException.class);

            // Resilience4j acquirePermission is void — use doThrow for void methods
            doThrow(exception).when(circuitBreaker).acquirePermission();

            assertThatThrownBy(() -> provider.acquirePermission()).isInstanceOf(CircuitBreakerOpenException.class);
        }

        @Test
        void getCircuitStateReturnsCurrent() {
            when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);
            assertThat(provider.getCircuitState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    class RateLimitDelegation {

        @Test
        void updateRateLimitDelegates() {
            HttpHeaders headers = new HttpHeaders();
            provider.updateRateLimit(42L, headers);
            verify(rateLimitTracker).updateFromHeaders(eq(42L), eq(headers));
        }

        @Test
        void isRateLimitCriticalDelegates() {
            when(rateLimitTracker.isCritical(42L)).thenReturn(true);
            assertThat(provider.isRateLimitCritical(42L)).isTrue();
            verify(rateLimitTracker).isCritical(42L);
        }

        @Test
        void waitIfRateLimitLowDelegates() throws InterruptedException {
            when(rateLimitTracker.waitIfNeeded(42L)).thenReturn(true);
            assertThat(provider.waitIfRateLimitLow(42L)).isTrue();
            verify(rateLimitTracker).waitIfNeeded(42L);
        }

        @Test
        void getRateLimitRemainingDelegates() {
            when(rateLimitTracker.getRemaining(42L)).thenReturn(50);
            assertThat(provider.getRateLimitRemaining(42L)).isEqualTo(50);
            verify(rateLimitTracker).getRemaining(42L);
        }

        @Test
        void getRateLimitResetAtDelegates() {
            Instant resetAt = Instant.parse("2026-02-15T10:30:00Z");
            when(rateLimitTracker.getResetAt(42L)).thenReturn(resetAt);
            assertThat(provider.getRateLimitResetAt(42L)).isEqualTo(resetAt);
            verify(rateLimitTracker).getResetAt(42L);
        }

        @Test
        void getRateLimitTrackerReturnsInstance() {
            assertThat(provider.getRateLimitTracker()).isSameAs(rateLimitTracker);
        }
    }
}
