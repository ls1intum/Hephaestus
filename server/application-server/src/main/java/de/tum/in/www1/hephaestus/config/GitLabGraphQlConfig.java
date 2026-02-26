package de.tum.in.www1.hephaestus.config;

import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_LIMIT;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_REMAINING;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_RESET;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.TRANSPORT_MAX_RETRIES;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpGraphQlClient;
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
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

/**
 * Configuration for GitLab GraphQL API client.
 * <p>
 * Creates a base {@link WebClient} and {@link HttpGraphQlClient} for GitLab API access.
 * Unlike the GitHub configuration ({@link GitHubGraphQlConfig}), this client has
 * <b>no hardcoded base URL</b> because GitLab URLs are per-workspace (self-hosted support).
 * The URL is set at request time by {@link de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider}.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Rate limit header logging ({@code RateLimit-Remaining}, {@code RateLimit-Reset})</li>
 *   <li>Retry with exponential backoff for 5xx errors and 429 rate limits</li>
 *   <li>Transport error retries for connection issues</li>
 *   <li>16MB response buffer for large GraphQL responses</li>
 * </ul>
 */
@Configuration
public class GitLabGraphQlConfig {

    private static final Logger log = LoggerFactory.getLogger(GitLabGraphQlConfig.class);

    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB

    // Retry configuration for GitLab 5xx and 429 errors
    private static final int MAX_RETRIES = 5;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    @Bean
    @Qualifier("gitLabGraphQlWebClient")
    public WebClient gitLabGraphQlWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(config -> {
                config.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE);
                config.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder());
                config.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder());
            })
            .build();

        ConnectionProvider connectionProvider = ConnectionProvider.builder("gitlab-graphql")
            .maxConnections(50)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(3))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(60))
            .lifo()
            .build();

        HttpClient httpClient = HttpClient.create(connectionProvider).responseTimeout(Duration.ofSeconds(135));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .exchangeStrategies(strategies)
            .filter(rateLimitLoggingFilter())
            .filter(retryFilter())
            .filter(transportErrorRetryFilter())
            .build();
    }

    @Bean
    @Qualifier("gitLabGraphQlClient")
    public HttpGraphQlClient gitLabGraphQlClient(@Qualifier("gitLabGraphQlWebClient") WebClient webClient) {
        return HttpGraphQlClient.builder(webClient).build();
    }

    /**
     * Logs GitLab rate limit information from response headers.
     * <p>
     * GitLab uses standard rate limit headers (not X-prefixed):
     * <ul>
     *   <li>{@code RateLimit-Limit}: Total points allowed per minute</li>
     *   <li>{@code RateLimit-Remaining}: Points remaining in current window</li>
     *   <li>{@code RateLimit-Reset}: Unix timestamp when window resets</li>
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

        String remaining = headers.getFirst(HEADER_RATE_LIMIT_REMAINING);
        String limit = headers.getFirst(HEADER_RATE_LIMIT_LIMIT);
        String reset = headers.getFirst(HEADER_RATE_LIMIT_RESET);

        if (remaining != null && limit != null) {
            try {
                int remainingInt = Integer.parseInt(remaining);
                int limitInt = Integer.parseInt(limit);
                double usagePercent = limitInt > 0 ? (100.0 * (limitInt - remainingInt)) / limitInt : 0;

                if (usagePercent > 80) {
                    log.warn(
                        "Approaching GitLab GraphQL rate limit: remaining={}, limit={}, usagePercent={}, resetEpoch={}",
                        remaining,
                        limit,
                        String.format("%.1f", usagePercent),
                        reset
                    );
                }
            } catch (NumberFormatException e) {
                log.debug("Could not parse GitLab rate limit headers: remaining={}, limit={}", remaining, limit);
            }
        }

        if (response.statusCode().value() == 429) {
            log.error("Exceeded GitLab rate limit: status=429, resetEpoch={}", reset);
        }
    }

    private ExchangeFilterFunction retryFilter() {
        return (request, next) ->
            next
                .exchange(request)
                .flatMap(response -> {
                    HttpStatusCode status = response.statusCode();

                    if (status.is4xxClientError() && status.value() != 429) {
                        return Mono.just(response);
                    }

                    if (status.is5xxServerError() || status.value() == 429) {
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
                                "Retrying GitLab GraphQL request: statusCode={}, attempt={}, maxRetries={}",
                                ex.getStatusCode(),
                                signal.totalRetries() + 1,
                                MAX_RETRIES
                            );
                        })
                        .onRetryExhaustedThrow((spec, signal) -> {
                            RetryableException ex = (RetryableException) signal.failure();
                            log.error(
                                "Failed GitLab GraphQL request after retries exhausted: retryCount={}, statusCode={}",
                                MAX_RETRIES,
                                ex.getStatusCode()
                            );
                            return new GitLabGraphQlException(
                                "GitLab GraphQL request failed after " + MAX_RETRIES + " retries",
                                ex.getStatusCode()
                            );
                        })
                );
    }

    private ExchangeFilterFunction transportErrorRetryFilter() {
        return (request, next) ->
            next
                .exchange(request)
                .retryWhen(
                    Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                        .maxBackoff(TRANSPORT_MAX_BACKOFF)
                        .jitter(JITTER_FACTOR)
                        .filter(GitLabGraphQlConfig::isTransportError)
                        .doBeforeRetry(signal -> {
                            Throwable failure = signal.failure();
                            log.warn(
                                "Retrying GitLab GraphQL request after transport error: errorType={}, message={}, attempt={}",
                                failure.getClass().getSimpleName(),
                                failure.getMessage(),
                                signal.totalRetries() + 1
                            );
                        })
                        .onRetryExhaustedThrow((spec, signal) -> {
                            Throwable failure = signal.failure();
                            log.error(
                                "Failed GitLab GraphQL request after transport retries exhausted: errorType={}, message={}",
                                failure.getClass().getSimpleName(),
                                failure.getMessage()
                            );
                            return new TransportException(
                                "GitLab GraphQL request failed after transport retries: " + failure.getMessage(),
                                failure
                            );
                        })
                );
    }

    /**
     * Determines if an exception is a transport-level error (connection reset, premature close).
     */
    private static boolean isTransportError(Throwable throwable) {
        if (throwable instanceof java.io.IOException) {
            return true;
        }
        String className = throwable.getClass().getName();
        return (
            className.contains("PrematureCloseException") ||
            className.contains("AbortedException") ||
            className.contains("ConnectionResetException")
        );
    }

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

    /** Exception thrown when GitLab GraphQL requests fail after exhausting retries. */
    public static class GitLabGraphQlException extends RuntimeException {

        private final int statusCode;

        public GitLabGraphQlException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /** Exception thrown when GitLab requests fail due to transport-level errors. */
    public static class TransportException extends RuntimeException {

        public TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
