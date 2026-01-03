package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.CircuitBreakerOpenException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService.InstallationToken;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Provides authenticated HttpGraphQlClient instances for GitHub GraphQL API.
 * <p>
 * This provider creates per-request authenticated clients by cloning the base
 * client
 * and injecting workspace-specific authentication tokens. Unlike the REST API
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
 *   public Mono<PullRequest> fetchPR(Long workspaceId, String owner, String repo, int number) {
 *     return clientProvider.forWorkspace(workspaceId)
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

    private static final Logger logger = LoggerFactory.getLogger(GitHubGraphQlClientProvider.class);

    private final HttpGraphQlClient baseClient;
    private final InstallationTokenProvider tokenProvider;
    private final GitHubAppTokenService appTokens;
    private final CircuitBreaker circuitBreaker;

    public GitHubGraphQlClientProvider(
        HttpGraphQlClient gitHubGraphQlClient,
        InstallationTokenProvider tokenProvider,
        GitHubAppTokenService appTokens,
        @Qualifier("githubGraphQlCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this.baseClient = gitHubGraphQlClient;
        this.tokenProvider = tokenProvider;
        this.appTokens = appTokens;
        this.circuitBreaker = circuitBreaker;
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
            logger.warn("GitHub GraphQL circuit breaker is OPEN - rejecting call");
            throw new CircuitBreakerOpenException("GitHub GraphQL API circuit breaker is open", e);
        }
    }

    /**
     * Returns an authenticated HttpGraphQlClient for the given workspace.
     * <p>
     * The client is created by cloning the base client and adding the appropriate
     * authentication header based on the workspace's git provider mode:
     * <ul>
     * <li>GitHub App Installation: Uses short-lived installation tokens</li>
     * <li>Personal Access Token: Uses the stored PAT</li>
     * </ul>
     *
     * @param workspaceId the workspace ID to authenticate for
     * @return authenticated HttpGraphQlClient ready for use
     * @throws IllegalArgumentException if workspace not found
     * @throws IllegalStateException    if workspace has invalid authentication
     *                                  config
     */
    public HttpGraphQlClient forWorkspace(Long workspaceId) {
        String token = getToken(workspaceId);
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

    private String getToken(Long workspaceId) {
        AuthMode authMode = tokenProvider.getAuthMode(workspaceId);

        if (authMode == AuthMode.GITHUB_APP) {
            Long installationId = tokenProvider
                .getInstallationId(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Workspace " + workspaceId + " has no installation id."));
            InstallationToken token = appTokens.getInstallationTokenDetails(installationId);
            return token.token();
        }

        return tokenProvider
            .getPersonalAccessToken(workspaceId)
            .filter(t -> !t.isBlank())
            .orElseThrow(() ->
                new IllegalStateException(
                    "Workspace " + workspaceId + " is configured for PAT access but no token is stored."
                )
            );
    }
}
