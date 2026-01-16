package de.tum.in.www1.hephaestus.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Resilience4j configuration for circuit breaker and rate limiter patterns.
 * <p>
 * Provides circuit breakers for external API calls to prevent cascading failures
 * when GitHub API becomes unavailable or experiences high error rates.
 * <p>
 * Also provides rate limiting for inbound API requests to comply with OWASP API4:2023
 * (Unrestricted Resource Consumption) security guidelines.
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
 * <p>
 * Rate Limiting (OWASP API4:2023):
 * <ul>
 * <li>100 requests per minute per client</li>
 * <li>Fail-fast behavior - rejected requests receive 429 Too Many Requests</li>
 * <li>Apply via {@code @RateLimiter(name = "api")} annotation</li>
 * </ul>
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    /**
     * Name of the circuit breaker for GitHub GraphQL API calls.
     */
    public static final String GITHUB_GRAPHQL_CIRCUIT_BREAKER = "githubGraphQl";

    /**
     * Name of the circuit breaker for GitHub REST API calls.
     */
    public static final String GITHUB_REST_CIRCUIT_BREAKER = "githubRest";

    /**
     * Name of the rate limiter for API endpoints (OWASP API4:2023 compliance).
     */
    public static final String API_RATE_LIMITER = "api";

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
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Register circuit breakers with event listeners for monitoring
        CircuitBreaker graphQlBreaker = registry.circuitBreaker(GITHUB_GRAPHQL_CIRCUIT_BREAKER);
        CircuitBreaker restBreaker = registry.circuitBreaker(GITHUB_REST_CIRCUIT_BREAKER);

        // Add event listeners for observability
        registerEventListeners(graphQlBreaker);
        registerEventListeners(restBreaker);

        log.info(
            "Initialized circuit breakers: graphQlBreaker={}, restBreaker={}",
            GITHUB_GRAPHQL_CIRCUIT_BREAKER,
            GITHUB_REST_CIRCUIT_BREAKER
        );

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
     * Provides the GitHub REST circuit breaker for injection.
     */
    @Bean
    public CircuitBreaker githubRestCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(GITHUB_REST_CIRCUIT_BREAKER);
    }

    /**
     * Creates a RateLimiterRegistry for API rate limiting (OWASP API4:2023 compliance).
     * <p>
     * Rate limiting protects against:
     * <ul>
     * <li>Denial of Service (DoS) attacks</li>
     * <li>Brute force attacks on authentication endpoints</li>
     * <li>Resource exhaustion from excessive API calls</li>
     * <li>Unfair resource consumption by single clients</li>
     * </ul>
     * <p>
     * Configuration:
     * <ul>
     * <li>100 requests per minute per client</li>
     * <li>Fail-fast behavior (no waiting for permits)</li>
     * </ul>
     * <p>
     * Usage: Apply {@code @RateLimiter(name = "api")} annotation to controller methods
     * or entire controller classes that need rate limiting.
     * <p>
     * Example:
     * <pre>{@code
     * @RestController
     * @RateLimiter(name = "api")
     * public class MyController {
     *     // All endpoints in this controller are rate limited
     * }
     * }</pre>
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            // Allow 100 requests per minute
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            // Fail fast - don't wait for permits, reject immediately
            .timeoutDuration(Duration.ofSeconds(0))
            .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);

        // Register the API rate limiter with event listeners
        RateLimiter apiRateLimiter = registry.rateLimiter(API_RATE_LIMITER);
        registerRateLimiterEventListeners(apiRateLimiter);

        log.info(
            "Initialized rate limiter: name={}, limitForPeriod={}, refreshPeriod={}",
            API_RATE_LIMITER,
            config.getLimitForPeriod(),
            config.getLimitRefreshPeriod()
        );

        return registry;
    }

    private void registerRateLimiterEventListeners(RateLimiter rateLimiter) {
        rateLimiter
            .getEventPublisher()
            .onSuccess(event -> log.trace("Rate limiter permitted request: name={}", event.getRateLimiterName()))
            .onFailure(event ->
                log.warn("Rate limiter rejected request: name={}, reason=limit_exceeded", event.getRateLimiterName())
            );
    }

    /**
     * Provides the API rate limiter for injection.
     */
    @Bean
    public RateLimiter apiRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter(API_RATE_LIMITER);
    }
}
