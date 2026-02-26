package de.tum.in.www1.hephaestus.config;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationSuspendedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Resilience4j configuration for circuit breaker patterns.
 * <p>
 * Provides circuit breakers for external API calls to prevent cascading failures
 * when GitHub API becomes unavailable or experiences high error rates.
 * <p>
 * Circuit Breaker States:
 * <ul>
 * <li><b>CLOSED:</b> Normal operation, requests pass through</li>
 * <li><b>OPEN:</b> Circuit tripped, requests fail fast without calling GitHub</li>
 * <li><b>HALF_OPEN:</b> Testing if GitHub is back, limited requests allowed</li>
 * </ul>
 * <p>
 * Configuration:
 * <ul>
 * <li>Sliding window of 10 calls</li>
 * <li>Opens circuit when failure rate exceeds 50%</li>
 * <li>Waits 30 seconds before attempting recovery</li>
 * <li>Allows 3 test calls in half-open state</li>
 * <li>Slow call threshold: 10 seconds (counts as failure if exceeded)</li>
 * </ul>
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    private final MeterRegistry meterRegistry;

    public ResilienceConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Exception types that indicate configuration problems, not transient service failures.
     * These should never trip the circuit breaker because:
     * <ul>
     *   <li>401/403 errors indicate credential/permission misconfiguration</li>
     *   <li>IllegalArgumentException indicates bad input, not service issues</li>
     * </ul>
     * <p>
     * IMPORTANT: Keycloak's RESTEasy client sometimes wraps these in ProcessingException,
     * so we must check the entire cause chain, not just the top-level exception.
     */
    private static final Set<Class<? extends Throwable>> CONFIG_ERROR_EXCEPTIONS = Set.of(
        NotAuthorizedException.class,
        ForbiddenException.class,
        IllegalArgumentException.class
    );

    /**
     * Checks if an exception (or any exception in its cause chain) indicates
     * a configuration error that should NOT trip the circuit breaker.
     * <p>
     * This handles the case where RESTEasy wraps NotAuthorizedException inside
     * ProcessingException during token refresh failures.
     *
     * @param throwable the exception to evaluate
     * @return true if this is a config error (should be ignored), false if infrastructure failure
     */
    static boolean isConfigurationError(Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> seen = new HashSet<>(); // Prevent infinite loops on circular causes
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
     * <p>
     * Returns true (record as failure) only for infrastructure failures:
     * <ul>
     *   <li>ProcessingException without auth errors in cause chain</li>
     *   <li>IOException (network/transport failures)</li>
     * </ul>
     * <p>
     * Returns false for configuration errors (401/403) even when wrapped.
     */
    static boolean shouldRecordAsFailure(Throwable throwable) {
        // First check: is this or any cause a configuration error?
        if (isConfigurationError(throwable)) {
            return false;
        }
        // Second check: is this an infrastructure failure type?
        return throwable instanceof ProcessingException || throwable instanceof IOException;
    }

    /**
     * Name of the circuit breaker for GitHub GraphQL API calls.
     */
    public static final String GITHUB_GRAPHQL_CIRCUIT_BREAKER = "githubGraphQl";

    /**
     * Name of the circuit breaker for GitLab GraphQL API calls.
     */
    public static final String GITLAB_GRAPHQL_CIRCUIT_BREAKER = "gitlabGraphQl";

    /**
     * Name of the circuit breaker for Keycloak Admin API calls.
     */
    public static final String KEYCLOAK_CIRCUIT_BREAKER = "keycloak";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Default configuration for GitHub API circuit breakers
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            // Use count-based sliding window
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            // Open circuit when 50% of calls fail
            .failureRateThreshold(50)
            // Also consider slow calls as failures
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            // Wait 30 seconds before transitioning from OPEN to HALF_OPEN
            .waitDurationInOpenState(Duration.ofSeconds(30))
            // Allow 3 calls in HALF_OPEN state to test if service recovered
            .permittedNumberOfCallsInHalfOpenState(3)
            // Minimum calls required before calculating failure rate
            .minimumNumberOfCalls(5)
            // Automatically transition from OPEN to HALF_OPEN after wait duration
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // Record these exceptions as failures
            .recordExceptions(
                IOException.class,
                TimeoutException.class,
                WebClientRequestException.class,
                WebClientResponseException.class
            )
            // Don't record these as failures (they're client errors, not service failures)
            .ignoreExceptions(
                IllegalArgumentException.class,
                IllegalStateException.class,
                InstallationSuspendedException.class
            )
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Register circuit breaker with event listeners for monitoring
        CircuitBreaker graphQlBreaker = registry.circuitBreaker(GITHUB_GRAPHQL_CIRCUIT_BREAKER);

        // Add event listeners for observability
        registerEventListeners(graphQlBreaker);

        // Register Micrometer metrics for all circuit breakers in the registry
        // Exposes metrics: resilience4j_circuitbreaker_state, resilience4j_circuitbreaker_calls_total, etc.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);

        log.info("Initialized circuit breaker: name={}", GITHUB_GRAPHQL_CIRCUIT_BREAKER);

        return registry;
    }

    private void registerEventListeners(CircuitBreaker circuitBreaker) {
        circuitBreaker
            .getEventPublisher()
            .onStateTransition(event ->
                log.warn(
                    "Circuit breaker state transition: breakerName={}, fromState={}, toState={}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()
                )
            )
            .onError(event ->
                log.debug(
                    "Circuit breaker recorded error: breakerName={}, exceptionType={}, message={}",
                    event.getCircuitBreakerName(),
                    event.getThrowable().getClass().getSimpleName(),
                    event.getThrowable().getMessage()
                )
            )
            .onSuccess(event ->
                log.trace(
                    "Circuit breaker recorded success: breakerName={}, durationMs={}",
                    event.getCircuitBreakerName(),
                    event.getElapsedDuration().toMillis()
                )
            )
            .onCallNotPermitted(event ->
                log.warn(
                    "Circuit breaker rejected call: breakerName={}, reason=circuit_open",
                    event.getCircuitBreakerName()
                )
            );
    }

    /**
     * Provides the GitHub GraphQL circuit breaker for injection.
     */
    @Bean
    public CircuitBreaker githubGraphQlCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(GITHUB_GRAPHQL_CIRCUIT_BREAKER);
    }

    /**
     * Provides the GitLab GraphQL circuit breaker for injection.
     * <p>
     * Uses the default circuit breaker configuration (same as GitHub GraphQL).
     * GitLab API failure patterns are similar enough that the same thresholds apply.
     */
    @Bean
    public CircuitBreaker gitlabGraphQlCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker breaker = registry.circuitBreaker(GITLAB_GRAPHQL_CIRCUIT_BREAKER);
        registerEventListeners(breaker);
        log.info("Initialized circuit breaker: name={}", GITLAB_GRAPHQL_CIRCUIT_BREAKER);
        return breaker;
    }

    /**
     * Provides the Keycloak circuit breaker for injection.
     * <p>
     * Uses a more lenient configuration than GitHub since Keycloak failures
     * are typically due to connectivity issues rather than rate limiting:
     * <ul>
     *   <li>Opens circuit after 3 consecutive failures (count-based)</li>
     *   <li>Waits 30 seconds before attempting recovery (fast recovery for transient issues)</li>
     *   <li>Allows 2 test calls in half-open state for reliable recovery detection</li>
     * </ul>
     * <p>
     * <b>Important:</b> Uses a custom predicate to check the entire exception cause chain.
     * This handles the case where Keycloak's RESTEasy client wraps NotAuthorizedException
     * inside ProcessingException during token refresh failures. Without cause chain inspection,
     * auth errors (which indicate config problems) would incorrectly trip the circuit.
     *
     * @see #shouldRecordAsFailure(Throwable)
     * @see #isConfigurationError(Throwable)
     */
    @Bean
    public CircuitBreaker keycloakCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig keycloakConfig = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .failureRateThreshold(60) // Open after 3 of 5 calls fail
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Reduced from 2 min for faster recovery
            .permittedNumberOfCallsInHalfOpenState(2) // Increased from 1 for reliable recovery detection
            .minimumNumberOfCalls(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // Use predicate to check ENTIRE cause chain for config errors
            // This correctly handles ProcessingException wrapping NotAuthorizedException
            .recordException(ResilienceConfig::shouldRecordAsFailure)
            .build();

        CircuitBreaker breaker = registry.circuitBreaker(KEYCLOAK_CIRCUIT_BREAKER, keycloakConfig);
        registerEventListeners(breaker);
        log.info("Initialized circuit breaker: name={}", KEYCLOAK_CIRCUIT_BREAKER);
        return breaker;
    }
}
