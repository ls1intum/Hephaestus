package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.CircuitBreakerOpenException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService.InstallationToken;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.RateLimitTracker;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRateLimit;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Provides authenticated HttpGraphQlClient instances for GitHub GraphQL API.
 * <p>
 * This provider creates per-request authenticated clients by cloning the base
 * client
 * and injecting scope-specific authentication tokens. Unlike the REST API
 * client
 * ({@link GitHubClientProvider}), GraphQL clients are lightweight and don't
 * need caching.
 * <p>
 * <b>Circuit Breaker Protection:</b>
 * All clients returned by this provider are protected by a circuit breaker
 * that prevents cascading failures when GitHub API is unavailable. When the
 * circuit is open, calls fail fast with {@link CircuitBreakerOpenException}.
 * <p>
 * Usage:
 *
 * <pre>{@code
 * @Service
 * public class PullRequestGraphQlService {
 *   private final GitHubGraphQlClientProvider clientProvider;
 *
 *   public Mono<PullRequest> fetchPR(Long scopeId, String owner, String repo, int number) {
 *     return clientProvider.forScope(scopeId)
 *         .document(GetPullRequestRequest.builder()
 *             .owner(owner)
 *             .name(repo)
 *             .number(number)
 *             .build()
 *             .toString())
 *         .retrieve("repository.pullRequest")
 *         .toEntity(PullRequest.class);
 *   }
 * }
 * }</pre>
 *
 * @see GitHubClientProvider for the REST API equivalent
 */
@Component
public class GitHubGraphQlClientProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubGraphQlClientProvider.class);

    private final HttpGraphQlClient baseClient;
    private final InstallationTokenProvider tokenProvider;
    private final GitHubAppTokenService appTokens;
    private final CircuitBreaker circuitBreaker;
    private final RateLimitTracker rateLimitTracker;

    public GitHubGraphQlClientProvider(
        HttpGraphQlClient gitHubGraphQlClient,
        InstallationTokenProvider tokenProvider,
        GitHubAppTokenService appTokens,
        @Qualifier("githubGraphQlCircuitBreaker") CircuitBreaker circuitBreaker,
        RateLimitTracker rateLimitTracker
    ) {
        this.baseClient = gitHubGraphQlClient;
        this.tokenProvider = tokenProvider;
        this.appTokens = appTokens;
        this.circuitBreaker = circuitBreaker;
        this.rateLimitTracker = rateLimitTracker;
    }

    /**
     * Checks if the circuit breaker allows calls to GitHub.
     * <p>
     * Use this to preemptively check if sync operations should be attempted.
     *
     * @return true if circuit is closed or half-open, false if open
     */
    public boolean isCircuitClosed() {
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    /**
     * Gets the current circuit breaker state for monitoring.
     *
     * @return current state (CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN)
     */
    public CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }

    /**
     * Records a successful API call for the circuit breaker.
     * <p>
     * Call this after a successful GraphQL operation completes.
     */
    public void recordSuccess() {
        circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a failed API call for the circuit breaker.
     * <p>
     * Call this when a GraphQL operation fails with a retryable error.
     *
     * @param throwable the exception that occurred
     */
    public void recordFailure(Throwable throwable) {
        circuitBreaker.onError(0, TimeUnit.MILLISECONDS, throwable);
    }

    /**
     * Checks if the circuit breaker permits a call, throwing if not.
     * <p>
     * Call this before making a GraphQL request to fail fast when circuit is open.
     *
     * @throws CircuitBreakerOpenException if the circuit is open
     */
    public void acquirePermission() {
        try {
            circuitBreaker.acquirePermission();
        } catch (CallNotPermittedException e) {
            log.warn("Rejected GraphQL call: reason=circuitBreakerOpen, state={}", circuitBreaker.getState());
            throw new CircuitBreakerOpenException("GitHub GraphQL API circuit breaker is open", e);
        }
    }

    /**
     * Returns an authenticated HttpGraphQlClient for the given scope.
     * <p>
     * The client is created by cloning the base client and adding the appropriate
     * authentication header based on the scope's git provider mode:
     * <ul>
     * <li>GitHub App Installation: Uses short-lived installation tokens</li>
     * <li>Personal Access Token: Uses the stored PAT</li>
     * </ul>
     *
     * @param scopeId the scope ID to authenticate for
     * @return authenticated HttpGraphQlClient ready for use
     * @throws IllegalArgumentException if scope not found
     * @throws IllegalStateException    if scope has invalid authentication
     *                                  config
     */
    public HttpGraphQlClient forScope(Long scopeId) {
        String token = getToken(scopeId);
        return baseClient.mutate().header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    /**
     * Returns an authenticated HttpGraphQlClient using the provided token.
     * <p>
     * Use this method when you already have a valid token (e.g., from a webhook
     * context
     * or cached installation token).
     *
     * @param token the OAuth/installation token
     * @return authenticated HttpGraphQlClient
     */
    public HttpGraphQlClient withToken(String token) {
        return baseClient.mutate().header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    // ========================================================================
    // Rate Limit Tracking (Per-Scope)
    // ========================================================================

    /**
     * Updates the rate limit tracker from a GraphQL response for a specific scope.
     * <p>
     * Call this method after every GraphQL query execution to keep the
     * rate limit tracking up to date. The rate limit data is extracted
     * from the "rateLimit" field in the response.
     *
     * @param scopeId the scope that made the API call
     * @param response the GraphQL response containing rate limit data
     * @return the extracted rate limit info, or null if not present
     */
    @Nullable
    public GHRateLimit trackRateLimit(Long scopeId, @Nullable ClientGraphQlResponse response) {
        return rateLimitTracker.updateFromResponse(scopeId, response);
    }

    /**
     * Gets the rate limit tracker for advanced rate limit management.
     *
     * @return the rate limit tracker instance
     */
    public RateLimitTracker getRateLimitTracker() {
        return rateLimitTracker;
    }

    /**
     * Checks if the rate limit is critically low for a scope and waits if necessary.
     * <p>
     * This method should be called before making GraphQL requests in loops
     * to proactively avoid hitting the rate limit.
     *
     * @param scopeId the scope to check
     * @return true if the method waited, false if no waiting was needed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean waitIfRateLimitLow(Long scopeId) throws InterruptedException {
        return rateLimitTracker.waitIfNeeded(scopeId);
    }

    /**
     * Checks if the rate limit is at a critical level for a scope.
     * <p>
     * Use this to decide whether to abort a sync operation early.
     *
     * @param scopeId the scope to check
     * @return true if rate limit is critically low
     */
    public boolean isRateLimitCritical(Long scopeId) {
        return rateLimitTracker.isCritical(scopeId);
    }

    /**
     * Gets the remaining rate limit points for a scope.
     *
     * @param scopeId the scope to check
     * @return remaining points
     */
    public int getRateLimitRemaining(Long scopeId) {
        return rateLimitTracker.getRemaining(scopeId);
    }

    /**
     * Gets the time when the rate limit resets for a scope.
     *
     * @param scopeId the scope to check
     * @return the reset instant, or null if unknown
     */
    public java.time.Instant getRateLimitResetAt(Long scopeId) {
        return rateLimitTracker.getResetAt(scopeId);
    }

    private String getToken(Long scopeId) {
        // Fail fast for suspended/inactive scopes - don't waste API calls
        if (!tokenProvider.isScopeActive(scopeId)) {
            throw new IllegalStateException(
                "Scope " + scopeId + " is not active (suspended or purged). Refusing to mint token."
            );
        }

        AuthMode authMode = tokenProvider.getAuthMode(scopeId);

        if (authMode == AuthMode.GITHUB_APP) {
            Long installationId = tokenProvider
                .getInstallationId(scopeId)
                .orElseThrow(() -> new IllegalStateException("Scope " + scopeId + " has no installation id."));
            InstallationToken token = appTokens.getInstallationTokenDetails(installationId);
            return token.token();
        }

        return tokenProvider
            .getPersonalAccessToken(scopeId)
            .filter(t -> !t.isBlank())
            .orElseThrow(() ->
                new IllegalStateException("Scope " + scopeId + " is configured for PAT access but no token is stored.")
            );
    }
}
