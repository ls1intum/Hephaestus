package de.tum.in.www1.hephaestus.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.config.KeycloakProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;

/**
 * Unit tests for {@link KeycloakUserRoleChecker} with Resilience4j circuit breaker.
 */
class KeycloakUserRoleCheckerTest extends BaseUnitTest {

    private static final String TEST_REALM = "test-realm";
    private static final String TEST_USERNAME = "test-user";
    private static final String TEST_USER_ID = "user-123";

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    @Mock
    private RoleMappingResource roleMappingResource;

    @Mock
    private RoleScopeResource roleScopeResource;

    private CircuitBreaker circuitBreaker;
    private KeycloakUserRoleChecker checker;

    /**
     * Exception types that indicate configuration problems, not transient service failures.
     * Mirrors the logic from ResilienceConfig for test independence.
     */
    private static final Set<Class<? extends Throwable>> CONFIG_ERROR_EXCEPTIONS = Set.of(
        NotAuthorizedException.class,
        ForbiddenException.class,
        IllegalArgumentException.class
    );

    /**
     * Checks if an exception (or any exception in its cause chain) indicates
     * a configuration error that should NOT trip the circuit breaker.
     */
    private static boolean isConfigurationError(Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            for (Class<? extends Throwable> configError : CONFIG_ERROR_EXCEPTIONS) {
                if (configError.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Determines if an exception should be recorded as a circuit breaker failure.
     */
    private static boolean shouldRecordAsFailure(Throwable throwable) {
        if (isConfigurationError(throwable)) {
            return false;
        }
        return throwable instanceof ProcessingException || throwable instanceof IOException;
    }

    /**
     * Creates a circuit breaker with test-friendly configuration.
     * Opens after 3 failures out of 5 calls (60% failure rate).
     * <p>
     * Uses the same predicate-based exception evaluation as production config:
     * checks the entire cause chain for auth errors (401/403).
     * This correctly handles ProcessingException wrapping NotAuthorizedException.
     */
    private CircuitBreaker createTestCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .failureRateThreshold(60)
            .minimumNumberOfCalls(3)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            // Use predicate to check ENTIRE cause chain for config errors
            // This correctly handles ProcessingException wrapping NotAuthorizedException
            .recordException(KeycloakUserRoleCheckerTest::shouldRecordAsFailure)
            .build();

        return CircuitBreakerRegistry.of(config).circuitBreaker("test-keycloak");
    }

    @BeforeEach
    void setUp() {
        circuitBreaker = createTestCircuitBreaker();
        KeycloakProperties keycloakProperties = new KeycloakProperties(
            "http://localhost:8081",
            TEST_REALM,
            "test-client",
            null,
            null,
            null,
            null
        );
        checker = new KeycloakUserRoleChecker(keycloak, keycloakProperties, circuitBreaker);

        when(keycloak.realm(TEST_REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    private void resetMocks() {
        org.mockito.Mockito.reset(usersResource, userResource, roleMappingResource, roleScopeResource);
        when(keycloak.realm(TEST_REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    private void setupUserWithRole(boolean hasRole) {
        resetMocks();
        UserRepresentation user = new UserRepresentation();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);

        when(usersResource.searchByUsername(TEST_USERNAME, true)).thenReturn(List.of(user));
        when(usersResource.get(TEST_USER_ID)).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

        if (hasRole) {
            RoleRepresentation role = new RoleRepresentation();
            role.setName("run_automatic_detection");
            when(roleScopeResource.listAll()).thenReturn(List.of(role));
        } else {
            when(roleScopeResource.listAll()).thenReturn(Collections.emptyList());
        }
    }

    private void setupAuthFailure() {
        resetMocks();
        when(usersResource.searchByUsername(anyString(), anyBoolean())).thenThrow(
            new NotAuthorizedException("Auth failed")
        );
    }

    private void setupForbiddenFailure() {
        resetMocks();
        when(usersResource.searchByUsername(anyString(), anyBoolean())).thenThrow(
            new ForbiddenException("Access denied")
        );
    }

    private void setupConnectionFailure() {
        resetMocks();
        when(usersResource.searchByUsername(anyString(), anyBoolean())).thenThrow(
            new ProcessingException("Connection refused")
        );
    }

    /**
     * Simulates the real Keycloak behavior where RESTEasy wraps NotAuthorizedException
     * inside ProcessingException during token refresh failures.
     * This is the bug case that the cause-chain checking predicate must handle.
     */
    private void setupWrappedAuthFailure() {
        resetMocks();
        NotAuthorizedException authException = new NotAuthorizedException("HTTP 401 Unauthorized");
        ProcessingException wrappedException = new ProcessingException(authException);
        when(usersResource.searchByUsername(anyString(), anyBoolean())).thenThrow(wrappedException);
    }

    /**
     * Simulates ProcessingException wrapping IOException (real infrastructure failure).
     * This SHOULD trip the circuit breaker.
     */
    private void setupWrappedIOFailure() {
        resetMocks();
        IOException ioException = new IOException("Connection reset by peer");
        ProcessingException wrappedException = new ProcessingException(ioException);
        when(usersResource.searchByUsername(anyString(), anyBoolean())).thenThrow(wrappedException);
    }

    @Nested
    @DisplayName("Normal Operation (Circuit Closed)")
    class NormalOperationTests {

        @Test
        @DisplayName("Should return true when user has role")
        void userHasRole() {
            setupUserWithRole(true);

            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isTrue();
            assertThat(checker.isHealthy()).isTrue();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Should return false when user does not have role")
        void userMissingRole() {
            setupUserWithRole(false);

            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isFalse();
            assertThat(checker.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("Should return false when user not found in Keycloak")
        void userNotFound() {
            when(usersResource.searchByUsername(TEST_USERNAME, true)).thenReturn(Collections.emptyList());

            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isFalse();
            assertThat(checker.isHealthy()).isTrue();
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Behavior")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should NOT open circuit after auth failures (401 is config error, not transient)")
        void authFailuresDoNotOpenCircuit() {
            setupAuthFailure();

            // Auth failures should be ignored by the circuit breaker
            // because they indicate credential misconfiguration, not service unavailability
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Circuit should remain closed - auth errors don't count as failures
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(checker.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("Should return false but continue working on auth failures")
        void authFailuresReturnFalseButDontBreakCircuit() {
            setupAuthFailure();

            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isFalse();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(checker.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("Should open circuit after three consecutive connection failures (60% of 5)")
        void connectionFailuresOpenCircuit() {
            setupConnectionFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(checker.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("Should skip requests when circuit is open")
        void circuitOpenSkipsRequests() {
            setupConnectionFailure();

            // Open the circuit with 3 connection failures
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Reset mock to verify no more calls
            org.mockito.Mockito.reset(usersResource);

            // Should skip without calling Keycloak
            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isFalse();
            verify(usersResource, times(0)).searchByUsername(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("ProcessingException (connection failure) should open circuit")
        void connectionFailureCountsAsFailure() {
            setupConnectionFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("ProcessingException wrapping NotAuthorizedException should NOT open circuit")
        void wrappedAuthFailuresDoNotOpenCircuit() {
            // This is the critical bug case: Keycloak wraps 401 in ProcessingException
            // Without cause-chain checking, this would incorrectly trip the circuit
            setupWrappedAuthFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Circuit should remain closed - wrapped auth errors still indicate config problems
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(checker.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("ProcessingException wrapping IOException SHOULD open circuit")
        void wrappedIOFailuresOpenCircuit() {
            // ProcessingException wrapping IOException is a real infrastructure failure
            // The cause-chain checking should NOT prevent this from tripping the circuit
            setupWrappedIOFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Circuit should open - this is a real infrastructure failure
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(checker.isHealthy()).isFalse();
        }
    }

    @Nested
    @DisplayName("Half-Open State and Recovery")
    class HalfOpenRecoveryTests {

        @Test
        @DisplayName("Should transition to half-open when manually triggered")
        void transitionsToHalfOpen() {
            setupConnectionFailure();

            // Open the circuit with connection failures
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Manually transition to half-open (simulating timeout elapsed)
            circuitBreaker.transitionToHalfOpenState();

            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }

        @Test
        @DisplayName("Should return to closed state if test request succeeds")
        void closesOnSuccess() {
            setupConnectionFailure();

            // Open the circuit with connection failures
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Transition to half-open
            circuitBreaker.transitionToHalfOpenState();

            // Setup success and make request
            setupUserWithRole(true);
            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isTrue();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Should return to open state if test request fails")
        void returnsToOpenOnTestFailure() {
            setupConnectionFailure();

            // Open the circuit with connection failures
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Transition to half-open
            circuitBreaker.transitionToHalfOpenState();

            // Test request fails again
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should allow multiple recovery cycles")
        void multipleRecoveryCycles() {
            // First circuit break (use connection failures, not auth failures)
            setupConnectionFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Recover
            circuitBreaker.transitionToHalfOpenState();
            setupUserWithRole(true);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // Second circuit break
            setupConnectionFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Recover again
            circuitBreaker.transitionToHalfOpenState();
            setupUserWithRole(false);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Should report healthy when circuit is closed")
        void healthyWhenClosed() {
            // Make a successful call first to use the stubs from setUp
            setupUserWithRole(false);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.isHealthy()).isTrue();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Should report unhealthy when circuit is open")
        void unhealthyWhenOpen() {
            // Use connection failure to open the circuit (auth failures don't open it)
            setupConnectionFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("Should remain healthy even with repeated auth failures")
        void healthyDespiteAuthFailures() {
            // Auth failures should not affect circuit health
            setupAuthFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Circuit should remain healthy - auth failures indicate config problems,
            // not service unavailability
            assertThat(checker.isHealthy()).isTrue();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Should NOT open circuit after forbidden failures (403 is config error, not transient)")
        void forbiddenFailuresDoNotOpenCircuit() {
            setupForbiddenFailure();

            // 403 failures should be ignored by the circuit breaker
            // because they indicate permission misconfiguration, not service unavailability
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Circuit should remain closed - forbidden errors don't count as failures
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(checker.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("Should report healthy in HALF_OPEN state to allow recovery traffic")
        void healthyWhenHalfOpen() {
            // Open the circuit with connection failures
            setupConnectionFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(checker.isHealthy()).isFalse();

            // Transition to half-open
            circuitBreaker.transitionToHalfOpenState();

            // HALF_OPEN should be considered healthy to allow recovery traffic
            // This is critical: treating HALF_OPEN as unhealthy would prevent recovery
            assertThat(checker.getCircuitState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
            assertThat(checker.isHealthy()).isTrue();
        }
    }
}
