package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.GitProviderMode;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceInstallationTokenProvider implements InstallationTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInstallationTokenProvider.class);

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceInstallationTokenProvider(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<Long> getInstallationId(Long scopeId) {
        return workspaceRepository.findById(scopeId).map(Workspace::getInstallationId);
    }

    @Override
    public Optional<String> getPersonalAccessToken(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(Workspace::getPersonalAccessToken)
            .filter(token -> token != null && !token.isBlank());
    }

    @Override
    public AuthMode getAuthMode(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(ws ->
                switch (ws.getGitProviderMode()) {
                    case GITHUB_APP_INSTALLATION -> AuthMode.GITHUB_APP;
                    case PAT_ORG -> AuthMode.PERSONAL_ACCESS_TOKEN;
                    case GITLAB_PAT -> throw new UnsupportedOperationException(
                        "GitLab auth mode not yet supported: " + ws.getGitProviderMode()
                    );
                    case null -> AuthMode.GITHUB_APP;
                }
            )
            .orElseGet(() -> {
                log.warn("Defaulted to GITHUB_APP auth mode: reason=scopeNotFound, scopeId={}", scopeId);
                return AuthMode.GITHUB_APP;
            });
    }

    @Override
    public boolean isScopeActive(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(ws -> ws.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .orElse(false);
    }
}
