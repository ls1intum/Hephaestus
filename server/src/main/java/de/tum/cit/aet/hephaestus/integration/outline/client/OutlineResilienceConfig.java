package de.tum.cit.aet.hephaestus.integration.outline.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience wiring for the Outline REST client, kept inside the Outline slice so the shared
 * {@code config.ResilienceConfig} carries no dependency on Outline exception types (that inbound edge
 * closed a module cycle). The circuit breaker reuses the shared {@link CircuitBreakerRegistry} bean so its
 * default thresholds and Micrometer metrics stay identical to the GitHub/GitLab breakers.
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(OutlineResilienceConfig.class);

    /** Shared name for the Outline circuit breaker and its bounded retry. */
    private static final String OUTLINE_REST = "outlineRestApi";

    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(1);

    /** Cap on an honored {@code Retry-After} so a single throttled call cannot hold the sync lock. */
    private static final Duration RETRY_AFTER_CAP = Duration.ofSeconds(60);

    /**
     * Circuit breaker guarding the document-sync RPC-over-POST calls so repeated Outline outages fail fast
     * instead of stalling the sync cycle. Uses the registry's default configuration (identical thresholds to
     * the GitHub/GitLab breakers).
     */
    @Bean
    public CircuitBreaker outlineRestApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker breaker = registry.circuitBreaker(OUTLINE_REST);
        breaker
            .getEventPublisher()
            .onStateTransition(event ->
                log.warn(
                    "Circuit breaker state transition: breakerName={}, fromState={}, toState={}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()
                )
            )
            .onCallNotPermitted(event ->
                log.warn(
                    "Circuit breaker rejected call: breakerName={}, reason=circuit_open",
                    event.getCircuitBreakerName()
                )
            );
        log.info("Initialized circuit breaker: name={}", OUTLINE_REST);
        return breaker;
    }

    /**
     * Bounded retry wrapping each Outline REST call. Retries only transient failures
     * ({@link OutlineApiException#isRetryable()} — 5xx, transport errors, and 429s). A 429 carrying a
     * {@link OutlineRateLimitedException#getRetryAfter() Retry-After} hint waits exactly that long (capped at
     * {@link #RETRY_AFTER_CAP}); every other transient failure uses exponential backoff with jitter.
     * Permanent failures (4xx, an open circuit) are not retried and propagate on the first attempt.
     */
    @Bean
    public Retry outlineRestApiRetry() {
        IntervalFunction backoff = IntervalFunction.ofExponentialRandomBackoff(RETRY_BASE_DELAY, 2.0, 0.5);
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(RETRY_MAX_ATTEMPTS)
            .intervalBiFunction((attempt, outcome) -> {
                Throwable failure = outcome.isLeft() ? outcome.getLeft() : null;
                if (failure instanceof OutlineRateLimitedException rle && rle.getRetryAfter() != null) {
                    return Math.min(rle.getRetryAfter().toMillis(), RETRY_AFTER_CAP.toMillis());
                }
                return backoff.apply(attempt);
            })
            .retryOnException(t -> t instanceof OutlineApiException oae && oae.isRetryable())
            .build();
        Retry retry = Retry.of(OUTLINE_REST, config);
        retry
            .getEventPublisher()
            .onRetry(event ->
                log.warn(
                    "Outline API retry: name={}, attempt={}, waitMs={}, lastError={}",
                    OUTLINE_REST,
                    event.getNumberOfRetryAttempts(),
                    event.getWaitInterval().toMillis(),
                    event.getLastThrowable() == null ? "n/a" : event.getLastThrowable().toString()
                )
            );
        log.info("Initialized retry: name={}", OUTLINE_REST);
        return retry;
    }
}
