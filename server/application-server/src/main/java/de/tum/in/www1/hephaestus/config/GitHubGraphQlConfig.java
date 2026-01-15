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
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
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

    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
    private static final double JITTER_FACTOR = 0.5;

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

        return WebClient.builder()
            .baseUrl(GITHUB_GRAPHQL_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .exchangeStrategies(strategies)
            .filter(rateLimitLoggingFilter())
            .filter(retryFilter())
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
     * Uses exponential backoff starting at 500ms, doubling each retry up to 10s,
     * with 50% jitter to prevent thundering herd.
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
}
