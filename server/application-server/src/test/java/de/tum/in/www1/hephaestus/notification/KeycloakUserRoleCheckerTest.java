package de.tum.in.www1.hephaestus.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.notification.KeycloakUserRoleChecker.CircuitState;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
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
 * Unit tests for {@link KeycloakUserRoleChecker} circuit breaker pattern.
 */
class KeycloakUserRoleCheckerTest extends BaseUnitTest {

    private static final String TEST_REALM = "test-realm";
    private static final String TEST_USERNAME = "test-user";
    private static final String TEST_USER_ID = "user-123";
    private static final Duration COOLDOWN_PERIOD = Duration.ofMinutes(5);

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

    private MutableClock testClock;
    private KeycloakUserRoleChecker checker;

    @BeforeEach
    void setUp() {
        testClock = new MutableClock(Instant.now());
        checker = new KeycloakUserRoleChecker(keycloak, TEST_REALM, testClock);

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

    private void setupConnectionFailure() {
        resetMocks();
        when(usersResource.searchByUsername(anyString(), anyBoolean())).thenThrow(
            new ProcessingException("Connection refused")
        );
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
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
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
            assertThat(checker.getConsecutiveFailures()).isZero();
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Behavior")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should NOT open circuit after single failure")
        void singleFailureDoesNotOpenCircuit() {
            setupAuthFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
            assertThat(checker.getConsecutiveFailures()).isEqualTo(1);
            assertThat(checker.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("Should NOT open circuit after two failures")
        void twoFailuresDoNotOpenCircuit() {
            setupAuthFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
            assertThat(checker.getConsecutiveFailures()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should open circuit after three consecutive failures")
        void threeFailuresOpenCircuit() {
            setupAuthFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);
            assertThat(checker.getConsecutiveFailures()).isEqualTo(3);
            assertThat(checker.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("Should skip requests when circuit is open")
        void circuitOpenSkipsRequests() {
            setupAuthFailure();

            // Open the circuit with 3 failures
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
        @DisplayName("Should reset failure count on success")
        void successResetsFailureCount() {
            setupAuthFailure();

            // Two failures
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getConsecutiveFailures()).isEqualTo(2);

            // Now succeed
            setupUserWithRole(true);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getConsecutiveFailures()).isZero();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
        }

        @Test
        @DisplayName("Should handle ProcessingException same as NotAuthorizedException")
        void connectionFailureCountsAsFailure() {
            setupConnectionFailure();

            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);
        }
    }

    @Nested
    @DisplayName("Half-Open State and Recovery")
    class HalfOpenRecoveryTests {

        @Test
        @DisplayName("Should transition to half-open after cooldown period")
        void transitionsToHalfOpenAfterCooldown() {
            setupAuthFailure();

            // Open the circuit
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);

            // Advance time past cooldown
            testClock.advance(COOLDOWN_PERIOD.plusSeconds(1));

            // Now setup success and make a request
            setupUserWithRole(true);
            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isTrue();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
        }

        @Test
        @DisplayName("Should remain open if cooldown not elapsed")
        void remainsOpenBeforeCooldown() {
            setupAuthFailure();

            // Open the circuit
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Advance time but NOT past cooldown
            testClock.advance(COOLDOWN_PERIOD.minusSeconds(1));

            // Request should be skipped
            boolean result = checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(result).isFalse();
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);
        }

        @Test
        @DisplayName("Should return to open state if test request fails")
        void returnsToOpenOnTestFailure() {
            setupAuthFailure();

            // Open the circuit
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Advance time past cooldown
            testClock.advance(COOLDOWN_PERIOD.plusSeconds(1));

            // Test request fails
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);
        }

        @Test
        @DisplayName("Should fully recover after successful test request")
        void fullyRecoversAfterSuccess() {
            setupAuthFailure();

            // Open the circuit
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            // Advance time past cooldown
            testClock.advance(COOLDOWN_PERIOD.plusSeconds(1));

            // Succeed
            setupUserWithRole(false);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);

            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
            assertThat(checker.getConsecutiveFailures()).isZero();
            assertThat(checker.isHealthy()).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle alternating success and failure")
        void alternatingSuccessAndFailure() {
            // Fail once
            setupAuthFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getConsecutiveFailures()).isEqualTo(1);

            // Succeed - resets counter
            setupUserWithRole(true);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getConsecutiveFailures()).isZero();

            // Fail again - starts from 1
            setupAuthFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getConsecutiveFailures()).isEqualTo(1);

            // Circuit should still be closed
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
        }

        @Test
        @DisplayName("Should allow multiple recovery cycles")
        void multipleRecoveryCycles() {
            // First circuit break
            setupAuthFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);

            // Recover
            testClock.advance(COOLDOWN_PERIOD.plusSeconds(1));
            setupUserWithRole(true);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);

            // Second circuit break
            setupAuthFailure();
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.OPEN);

            // Recover again
            testClock.advance(COOLDOWN_PERIOD.plusSeconds(1));
            setupUserWithRole(false);
            checker.hasAutomaticDetectionRole(TEST_USERNAME);
            assertThat(checker.getCircuitState()).isEqualTo(CircuitState.CLOSED);
        }
    }

    /**
     * Mutable clock for testing time-dependent behavior.
     */
    private static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
