package de.tum.cit.aet.hephaestus.config;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.exception.InstallationSuspendedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
     * Name of the circuit breaker for GitHub GraphQL API calls.
     */
    public static final String GITHUB_GRAPHQL_CIRCUIT_BREAKER = "githubGraphQl";

    /**
     * Name of the circuit breaker for GitLab GraphQL API calls.
     */
    public static final String GITLAB_GRAPHQL_CIRCUIT_BREAKER = "gitlabGraphQl";

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
    @ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
    public CircuitBreaker gitlabGraphQlCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker breaker = registry.circuitBreaker(GITLAB_GRAPHQL_CIRCUIT_BREAKER);
        registerEventListeners(breaker);
        log.info("Initialized circuit breaker: name={}", GITLAB_GRAPHQL_CIRCUIT_BREAKER);
        return breaker;
    }
}
