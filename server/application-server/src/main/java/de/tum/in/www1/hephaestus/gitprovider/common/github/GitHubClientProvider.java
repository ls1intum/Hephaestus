package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitHubClientProvider {

    private final WorkspaceRepository workspaceRepository;
    private final GitHubAppTokenService appTokens;
    private final String legacyPat;

    public GitHubClientProvider(
        WorkspaceRepository workspaceRepository,
        GitHubAppTokenService appTokens,
        @Value("${github.auth-token:}") String legacyPat
    ) {
        this.workspaceRepository = workspaceRepository;
        this.appTokens = appTokens;
        this.legacyPat = legacyPat;
    }

    /**
     * Get a GitHub client for a specific installation.
     * Falls back to PAT if no installationId is provided.
     */
    public GitHub forInstallationOrPat(Long installationId) throws IOException {
        if (installationId != null) {
            return appTokens.clientForInstallation(installationId);
        }
        if (legacyPat == null || legacyPat.isBlank()) {
            throw new IllegalStateException(
                "No GitHub App installation linked and no legacy PAT (github.auth-token) configured."
            );
        }
        return new GitHubBuilder().withOAuthToken(legacyPat).build();
    }

    /**
     * Get a GitHub client for the given workspace.
     */
    public GitHub forWorkspace(Long workspaceId) throws IOException {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        return forInstallationOrPat(workspace.getInstallationId());
    }
}
