package de.tum.in.www1.hephaestus.gitprovider.common.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService.InstallationToken;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.GitProviderMode;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.springframework.stereotype.Component;

@Component
public class GitHubClientProvider {

    private final WorkspaceRepository workspaceRepository;
    private final GitHubAppTokenService appTokens;

    private final Cache<Long, GitHubClientHolder> workspaceClients = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(50))
        .maximumSize(10_000)
        .build();

    public GitHubClientProvider(WorkspaceRepository workspaceRepository, GitHubAppTokenService appTokens) {
        this.workspaceRepository = workspaceRepository;
        this.appTokens = appTokens;
    }

    /**
     * Return a cached Hub4J {@link GitHub} client for the given workspace id.
     * <p>
     * The provider transparently handles both GitHub App installations and PAT-backed workspaces,
     * creating short-lived clients on-demand and reusing them for up to 50 minutes to avoid hitting
     * rate limits or issuing unnecessary installation tokens.
     */
    public GitHub forWorkspace(Long workspaceId) throws IOException {
        try {
            GitHubClientHolder holder = workspaceClients.get(workspaceId, this::createClientForWorkspace);
            if (holder == null) {
                throw new IllegalStateException("Workspace client missing for " + workspaceId);
            }

            if (holder.needsRefresh()) {
                workspaceClients.invalidate(workspaceId);
                holder = workspaceClients.get(workspaceId, this::createClientForWorkspace);
            }

            return holder.client();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Evict all cached clients associated with the supplied installation id.
     * <p>
     * This is primarily invoked when we update installation metadata (e.g. after rotating tokens),
     * ensuring that subsequent calls obtain a fresh client.
     */
    public void invalidateInstallation(Long installationId) {
        if (installationId == null) {
            return;
        }
        workspaceRepository
            .findByInstallationId(installationId)
            .map(Workspace::getId)
            .ifPresent(workspaceClients::invalidate);
    }

    /**
     * Evict the cached client for the given workspace, forcing the next lookup to build a new instance.
     */
    public void invalidateWorkspace(Long workspaceId) {
        workspaceClients.invalidate(workspaceId);
    }

    @PreDestroy
    public void shutdown() {
        workspaceClients.invalidateAll();
    }

    private GitHubClientHolder createClientForWorkspace(Long workspaceId) {
        try {
            Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

            if (workspace.getGitProviderMode() == GitProviderMode.GITHUB_APP_INSTALLATION) {
                Long installationId = workspace.getInstallationId();
                if (installationId == null) {
                    throw new IllegalStateException("Workspace " + workspaceId + " has no installation id.");
                }
                InstallationToken token = appTokens.getInstallationTokenDetails(installationId);
                Instant refreshAt = token.expiresAt().minus(Duration.ofMinutes(2));
                if (refreshAt.isBefore(Instant.now())) {
                    refreshAt = Instant.now();
                }
                GitHub client = createGitHubClient(token.token());
                return new GitHubClientHolder(client, refreshAt);
            }

            String token = workspace.getPersonalAccessToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException(
                    "Workspace " + workspaceId + " is configured for PAT access but no token is stored."
                );
            }

            GitHub client = createGitHubClient(token);
            return new GitHubClientHolder(client, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record GitHubClientHolder(GitHub client, Instant refreshAt) {
        boolean needsRefresh() {
            return refreshAt != null && Instant.now().isAfter(refreshAt);
        }
    }

    /**
     * Creates a GitHub client with automatic rate limit error handling.
     * Uses WAIT handlers to automatically retry when rate limits are hit.
     */
    private GitHub createGitHubClient(String token) throws IOException {
        return new GitHubBuilder()
            .withOAuthToken(token)
            .withRateLimitHandler(GitHubRateLimitHandler.WAIT)
            .withAbuseLimitHandler(GitHubAbuseLimitHandler.WAIT)
            .build();
    }
}
