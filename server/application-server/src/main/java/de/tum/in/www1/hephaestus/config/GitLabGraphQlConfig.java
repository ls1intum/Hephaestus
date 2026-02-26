package de.tum.in.www1.hephaestus.config;

import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_LIMIT;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_REMAINING;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.HEADER_RATE_LIMIT_RESET;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants.TRANSPORT_MAX_RETRIES;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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
 * Only loaded when {@code hephaestus.gitlab.enabled=true}. Creates a base
 * {@link WebClient} and {@link HttpGraphQlClient} for GitLab API access.
 * No hardcoded base URL — URLs are per-workspace (self-hosted support).
 */
@Configuration
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
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
            .codecs(config -> config.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
            .build();

        // GitLab rate limit is 100 points/min — 10 connections is generous
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gitlab-graphql")
            .maxConnections(10)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(3))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(60))
            .build();

        // 75s response timeout covers the extended GraphQL timeout (60s) with margin
        HttpClient httpClient = HttpClient.create(connectionProvider).responseTimeout(Duration.ofSeconds(75));

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
                        return response.releaseBody().then(Mono.error(new RetryableStatusException(status.value())));
                    }

                    return Mono.just(response);
                })
                .retryWhen(
                    Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(JITTER_FACTOR)
                        .filter(throwable -> throwable instanceof RetryableStatusException)
                        .doBeforeRetry(signal -> {
                            var ex = (RetryableStatusException) signal.failure();
                            log.warn(
                                "Retrying GitLab GraphQL request: statusCode={}, attempt={}, maxRetries={}",
                                ex.getStatusCode(),
                                signal.totalRetries() + 1,
                                MAX_RETRIES
                            );
                        })
                        .onRetryExhaustedThrow((spec, signal) -> {
                            var ex = (RetryableStatusException) signal.failure();
                            log.error(
                                "Failed GitLab GraphQL request after retries exhausted: retryCount={}, statusCode={}",
                                MAX_RETRIES,
                                ex.getStatusCode()
                            );
                            return new RuntimeException(
                                "GitLab GraphQL request failed after " +
                                    MAX_RETRIES +
                                    " retries (status=" +
                                    ex.getStatusCode() +
                                    ")"
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
                        .filter(GitHubTransportErrors::isTransportError)
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
                            return new RuntimeException(
                                "GitLab GraphQL request failed after transport retries: " + failure.getMessage(),
                                failure
                            );
                        })
                );
    }

    /**
     * Internal exception for retryable HTTP status codes (5xx, 429).
     */
    private static class RetryableStatusException extends RuntimeException {

        private final int statusCode;

        RetryableStatusException(int statusCode) {
            super("Retryable HTTP error: " + statusCode);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }
    }
}
