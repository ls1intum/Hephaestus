package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService.InstallationToken;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.GitProviderMode;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
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
 * Usage:
 *
 * <pre>{@code
 * @Service
 * public class PullRequestGraphQlService {
 *   private final GitHubGraphQlClientProvider clientProvider;
 *   private final WorkspaceRepository workspaceRepo;
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

    private final HttpGraphQlClient baseClient;
    private final WorkspaceRepository workspaceRepository;
    private final GitHubAppTokenService appTokens;

    public GitHubGraphQlClientProvider(
        HttpGraphQlClient gitHubGraphQlClient,
        WorkspaceRepository workspaceRepository,
        GitHubAppTokenService appTokens
    ) {
        this.baseClient = gitHubGraphQlClient;
        this.workspaceRepository = workspaceRepository;
        this.appTokens = appTokens;
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
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        String token = getToken(workspace);

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

    private String getToken(Workspace workspace) {
        if (workspace.getGitProviderMode() == GitProviderMode.GITHUB_APP_INSTALLATION) {
            Long installationId = workspace.getInstallationId();
            if (installationId == null) {
                throw new IllegalStateException("Workspace " + workspace.getId() + " has no installation id.");
            }
            InstallationToken token = appTokens.getInstallationTokenDetails(installationId);
            return token.token();
        }

        String token = workspace.getPersonalAccessToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "Workspace " + workspace.getId() + " is configured for PAT access but no token is stored."
            );
        }
        return token;
    }
}
