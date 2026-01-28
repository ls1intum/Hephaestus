package de.tum.in.www1.hephaestus.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.config.jackson.GitHubActorMixin;
import de.tum.in.www1.hephaestus.config.jackson.GitHubRepositoryOwnerMixin;
import de.tum.in.www1.hephaestus.config.jackson.GitHubRequestedReviewerMixin;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHActor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepositoryOwner;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRequestedReviewer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.graphql.support.ResourceDocumentSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

/**
 * Production-grade configuration for GitHub GraphQL API client.
 * <p>
 * Features:
 * <ul>
 * <li><b>Rate Limit Tracking:</b> Logs GitHub rate limit usage from response
 * headers</li>
 * <li><b>Retry with Backoff:</b> Automatic retries with exponential backoff for
 * 5xx errors</li>
 * <li><b>Document Loading:</b> Loads queries from
 * {@code classpath:graphql-documents/}</li>
 * </ul>
 *
 * @see org.springframework.graphql.client.HttpGraphQlClient
 */
@Configuration
public class GitHubGraphQlConfig {

    private static final Logger log = LoggerFactory.getLogger(GitHubGraphQlConfig.class);

    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB

    // Retry configuration for GitHub 502/504 errors and transport-level failures
    // GitHub infrastructure errors typically resolve within 30-60 seconds.
    // These values provide ~135 seconds of total retry time:
    // Attempt 1: ~5s, Attempt 2: ~10s, Attempt 3: ~20s, Attempt 4: ~40s, Attempt 5: ~60s (capped)
    private static final int MAX_RETRIES = 5;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);
    private static final double JITTER_FACTOR = 0.5;

    // Transport-level retry configuration for connection-level errors
    // (PrematureCloseException, connection resets during response streaming)
    // Uses fewer retries with shorter backoff since these are network-level issues
    private static final int TRANSPORT_MAX_RETRIES = 3;
    private static final Duration TRANSPORT_INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration TRANSPORT_MAX_BACKOFF = Duration.ofSeconds(15);

    // GitHub rate limit headers
    private static final String HEADER_RATE_LIMIT = "x-ratelimit-limit";
    private static final String HEADER_RATE_REMAINING = "x-ratelimit-remaining";
    private static final String HEADER_RATE_RESET = "x-ratelimit-reset";
    private static final String HEADER_RATE_USED = "x-ratelimit-used";
    private static final String HEADER_RETRY_AFTER = "retry-after";

    // Rate limit metrics - exposed via Micrometer for dashboards/alerting
    private final AtomicInteger rateLimitRemaining = new AtomicInteger(0);
    private final AtomicInteger rateLimitLimit = new AtomicInteger(0);
    private final AtomicInteger rateLimitUsed = new AtomicInteger(0);

    public GitHubGraphQlConfig(MeterRegistry meterRegistry) {
        // Register gauges for rate limit monitoring
        // These are more appropriate than logs for continuous monitoring
        Gauge.builder("github.graphql.ratelimit.remaining", rateLimitRemaining, AtomicInteger::get)
            .description("GitHub GraphQL API rate limit points remaining")
            .register(meterRegistry);
        Gauge.builder("github.graphql.ratelimit.limit", rateLimitLimit, AtomicInteger::get)
            .description("GitHub GraphQL API rate limit total points")
            .register(meterRegistry);
        Gauge.builder("github.graphql.ratelimit.used", rateLimitUsed, AtomicInteger::get)
            .description("GitHub GraphQL API rate limit points used")
            .register(meterRegistry);
    }

    @Bean
    public WebClient gitHubGraphQlWebClient(ObjectMapper baseObjectMapper) {
        // Create a custom ObjectMapper for GitHub GraphQL that:
        // 1. Uses Long for integers (GitHub databaseId values exceed 32-bit int range)
        // 2. Registers mixins for polymorphic interface deserialization (Actor, RequestedReviewer, RepositoryOwner)
        ObjectMapper graphQlObjectMapper = baseObjectMapper
            .copy()
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .addMixIn(GHActor.class, GitHubActorMixin.class)
            .addMixIn(GHRequestedReviewer.class, GitHubRequestedReviewerMixin.class)
            .addMixIn(GHRepositoryOwner.class, GitHubRepositoryOwnerMixin.class);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(config -> {
                config.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE);
                config.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(graphQlObjectMapper));
                config.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(graphQlObjectMapper));
            })
            .build();

        // Configure connection pool for efficient connection reuse.
        // This prevents creating new connections for each request and provides:
        // - Connection reuse across requests (reduces latency)
        // - Proper cleanup of idle connections
        // - Limits to prevent resource exhaustion
        //
        // NOTE: We use aggressive eviction settings to mitigate PrematureCloseException.
        // GitHub's load balancers may close connections after ~60s of idle time.
        // By recycling connections more frequently, we reduce the chance of using
        // stale connections that will be closed mid-response.
        ConnectionProvider connectionProvider = ConnectionProvider.builder("github-graphql")
            .maxConnections(50) // Max concurrent connections to GitHub
            .maxIdleTime(Duration.ofSeconds(20)) // Close idle connections after 20s (reduced from 30s)
            .maxLifeTime(Duration.ofMinutes(3)) // Recycle connections after 3 min (reduced from 5 min)
            .pendingAcquireTimeout(Duration.ofSeconds(60)) // Timeout waiting for connection
            .evictInBackground(Duration.ofSeconds(60)) // Background cleanup interval (reduced from 120s)
            .lifo() // Use last-in-first-out to prefer fresh connections
            .build();

        HttpClient httpClient = HttpClient.create(connectionProvider).responseTimeout(Duration.ofSeconds(90)); // Response timeout for slow responses

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(GITHUB_GRAPHQL_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .exchangeStrategies(strategies)
            .filter(rateLimitLoggingFilter())
            .filter(retryFilter())
            .filter(transportErrorRetryFilter())
            .build();
    }

    @Bean
    public HttpGraphQlClient gitHubGraphQlClient(WebClient gitHubGraphQlWebClient) {
        // Configure document source to load operations from the correct location
        // Operations are colocated with the GitHub schema at graphql/github/operations/
        ResourceDocumentSource documentSource = new ResourceDocumentSource(
            List.of(new ClassPathResource("graphql/github/operations/")),
            List.of(".graphql", ".gql")
        );
        return HttpGraphQlClient.builder(gitHubGraphQlWebClient).documentSource(documentSource).build();
    }

    /**
     * Logs GitHub rate limit information from response headers.
     * <p>
     * GitHub returns these headers on every response:
     * <ul>
     * <li>{@code x-ratelimit-limit}: Total points allowed per hour</li>
     * <li>{@code x-ratelimit-remaining}: Points remaining in current window</li>
     * <li>{@code x-ratelimit-used}: Points used in current window</li>
     * <li>{@code x-ratelimit-reset}: Unix timestamp when limit resets</li>
     * </ul>
     */
    private ExchangeFilterFunction rateLimitLoggingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            logRateLimitInfo(response);
            return Mono.just(response);
        });
    }

    private void logRateLimitInfo(ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();

        String remaining = headers.getFirst(HEADER_RATE_REMAINING);
        String limit = headers.getFirst(HEADER_RATE_LIMIT);
        String used = headers.getFirst(HEADER_RATE_USED);
        String reset = headers.getFirst(HEADER_RATE_RESET);

        if (remaining != null && limit != null) {
            int remainingInt = Integer.parseInt(remaining);
            int limitInt = Integer.parseInt(limit);
            int usedInt = used != null ? Integer.parseInt(used) : limitInt - remainingInt;

            // Update metrics for dashboard monitoring (best practice: metrics over logs)
            rateLimitRemaining.set(remainingInt);
            rateLimitLimit.set(limitInt);
            rateLimitUsed.set(usedInt);

            // Only log actionable events - approaching limit is a warning condition
            double usagePercent = (100.0 * (limitInt - remainingInt)) / limitInt;
            if (usagePercent > 80) {
                log.warn(
                    "Approaching GitHub GraphQL rate limit: remaining={}, limit={}, usagePercent={}, resetEpoch={}",
                    remaining,
                    limit,
                    String.format("%.1f", usagePercent),
                    reset
                );
            }
            // Routine rate limit status is available via metrics endpoint, not logs
        }

        // Special handling for 429 Too Many Requests - this is an error condition
        if (response.statusCode().value() == 429) {
            String retryAfter = headers.getFirst(HEADER_RETRY_AFTER);
            log.error("Exceeded GitHub rate limit: retryAfterSeconds={}", retryAfter);
        }
    }

    /**
     * Retry filter with exponential backoff for transient errors.
     * <p>
     * Retries on:
     * <ul>
     * <li>5xx server errors (GitHub infrastructure issues)</li>
     * <li>429 Too Many Requests (rate limited)</li>
     * </ul>
     * <p>
     * Uses exponential backoff starting at 5 seconds, doubling each retry up to 60 seconds,
     * with 50% jitter to prevent thundering herd. This provides approximately 135 seconds
     * of total retry time, which is sufficient for GitHub's typical 502/504 recovery time
     * of 30-60 seconds.
     */
    private ExchangeFilterFunction retryFilter() {
        return (request, next) ->
            next
                .exchange(request)
                .flatMap(response -> {
                    HttpStatusCode status = response.statusCode();

                    // Don't retry client errors (4xx) except 429
                    if (status.is4xxClientError() && status.value() != 429) {
                        return Mono.just(response);
                    }

                    // Retry on 5xx and 429
                    if (status.is5xxServerError() || status.value() == 429) {
                        // Release the body to prevent resource leaks
                        return response.releaseBody().then(Mono.error(new RetryableException(status.value())));
                    }

                    return Mono.just(response);
                })
                .retryWhen(
                    Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(JITTER_FACTOR)
                        .filter(throwable -> throwable instanceof RetryableException)
                        .doBeforeRetry(signal -> {
                            RetryableException ex = (RetryableException) signal.failure();
                            log.warn(
                                "Retrying GitHub GraphQL request: statusCode={}, attempt={}, maxRetries={}",
                                ex.getStatusCode(),
                                signal.totalRetries() + 1,
                                MAX_RETRIES
                            );
                        })
                        .onRetryExhaustedThrow((spec, signal) -> {
                            RetryableException ex = (RetryableException) signal.failure();
                            log.error(
                                "Failed GitHub GraphQL request after retries exhausted: retryCount={}, statusCode={}",
                                MAX_RETRIES,
                                ex.getStatusCode()
                            );
                            return new GitHubGraphQlException(
                                "GitHub GraphQL request failed after " + MAX_RETRIES + " retries",
                                ex.getStatusCode()
                            );
                        })
                );
    }

    /**
     * Retry filter for transport-level errors that occur during response streaming.
     * <p>
     * This handles errors like {@code PrematureCloseException} which occur when:
     * <ul>
     *   <li>GitHub closes the connection mid-response (large payloads, infrastructure issues)</li>
     *   <li>Network intermediaries (load balancers, proxies) timeout during streaming</li>
     *   <li>Stale keep-alive connections are reused after server-side timeout</li>
     * </ul>
     * <p>
     * These errors are distinct from HTTP status code errors (handled by {@link #retryFilter()})
     * because they occur at the transport layer, after the HTTP status has been received.
     * <p>
     * Uses shorter backoff than HTTP errors since transport issues often resolve quickly.
     */
    private ExchangeFilterFunction transportErrorRetryFilter() {
        return (request, next) ->
            next
                .exchange(request)
                .retryWhen(
                    Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                        .maxBackoff(TRANSPORT_MAX_BACKOFF)
                        .jitter(JITTER_FACTOR)
                        .filter(this::isTransportError)
                        .doBeforeRetry(signal -> {
                            Throwable failure = signal.failure();
                            log.warn(
                                "Retrying GitHub GraphQL request after transport error: errorType={}, message={}, attempt={}, maxRetries={}",
                                failure.getClass().getSimpleName(),
                                failure.getMessage(),
                                signal.totalRetries() + 1,
                                TRANSPORT_MAX_RETRIES
                            );
                        })
                        .onRetryExhaustedThrow((spec, signal) -> {
                            Throwable failure = signal.failure();
                            log.error(
                                "Failed GitHub GraphQL request after transport retries exhausted: retryCount={}, errorType={}, message={}",
                                TRANSPORT_MAX_RETRIES,
                                failure.getClass().getSimpleName(),
                                failure.getMessage()
                            );
                            return new TransportException(
                                "GitHub GraphQL request failed after " +
                                    TRANSPORT_MAX_RETRIES +
                                    " retries due to transport error: " +
                                    failure.getMessage(),
                                failure
                            );
                        })
                );
    }

    /**
     * Determines if an exception is a transport-level error that should be retried.
     * <p>
     * Transport errors occur at the network/connection level, distinct from HTTP errors:
     * <ul>
     *   <li>{@code PrematureCloseException}: Connection closed during response streaming</li>
     *   <li>{@code IOException}: General I/O failures during communication</li>
     *   <li>Connection reset/abort exceptions</li>
     * </ul>
     *
     * @param throwable the exception to check
     * @return true if this is a retryable transport error
     */
    private boolean isTransportError(Throwable throwable) {
        // Unwrap common wrapper exceptions
        Throwable cause = throwable;
        while (cause != null) {
            // PrematureCloseException: Connection closed during response streaming
            // This is the specific error we're targeting
            if (cause instanceof PrematureCloseException) {
                return true;
            }

            // Check for connection-related exceptions by class name
            // (to avoid hard dependencies on internal reactor-netty classes)
            String className = cause.getClass().getName();
            if (
                className.contains("PrematureCloseException") ||
                className.contains("AbortedException") ||
                className.contains("ConnectionResetException")
            ) {
                return true;
            }

            // Check for specific IOException subclasses indicating connection issues
            if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null) {
                    String lowerMessage = message.toLowerCase();
                    if (
                        lowerMessage.contains("connection reset") ||
                        lowerMessage.contains("broken pipe") ||
                        lowerMessage.contains("connection abort") ||
                        lowerMessage.contains("premature") ||
                        lowerMessage.contains("stream closed")
                    ) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Internal exception for retryable HTTP errors.
     */
    private static class RetryableException extends RuntimeException {

        private final int statusCode;

        RetryableException(int statusCode) {
            super("Retryable HTTP error: " + statusCode);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }
    }

    /**
     * Exception thrown when GitHub GraphQL requests fail after exhausting retries.
     */
    public static class GitHubGraphQlException extends RuntimeException {

        private final int statusCode;

        public GitHubGraphQlException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /**
     * Exception thrown when GitHub GraphQL requests fail due to transport-level errors
     * (connection issues, premature close, etc.) after exhausting retries.
     */
    public static class TransportException extends RuntimeException {

        public TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
