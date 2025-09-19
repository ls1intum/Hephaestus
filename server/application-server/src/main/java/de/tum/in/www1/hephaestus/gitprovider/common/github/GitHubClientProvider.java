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

    /** App-scoped client (JWT). */
    public GitHub asApp() throws IOException {
        return appTokens.clientAsApp();
    }

    /**
     * Client for the first workspace. Prefers installation token; falls back to PAT if configured.
     * Throws if neither is available.
     */
    public GitHub forDefaultWorkspace() throws IOException {
        Workspace workspace = workspaceRepository.findFirstByOrderByIdAsc().orElseThrow();
        if (workspace.getInstallationId() != null) {
            return appTokens.clientForInstallation(workspace.getInstallationId());
        }
        if (legacyPat == null || legacyPat.isBlank()) {
            throw new IllegalStateException(
                "No GitHub App installation linked and no legacy PAT (github.auth-token) configured."
            );
        }
        return new GitHubBuilder().withOAuthToken(legacyPat).build();
    }
}
