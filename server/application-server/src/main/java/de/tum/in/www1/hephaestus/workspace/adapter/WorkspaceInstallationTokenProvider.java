package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.GitProviderMode;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceInstallationTokenProvider implements InstallationTokenProvider {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceInstallationTokenProvider(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<Long> getInstallationId(Long workspaceId) {
        return workspaceRepository.findById(workspaceId).map(Workspace::getInstallationId);
    }

    @Override
    public Optional<String> getPersonalAccessToken(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(Workspace::getPersonalAccessToken)
            .filter(token -> token != null && !token.isBlank());
    }

    @Override
    public AuthMode getAuthMode(Long workspaceId) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        return workspace.getGitProviderMode() == GitProviderMode.GITHUB_APP_INSTALLATION
            ? AuthMode.GITHUB_APP_INSTALLATION
            : AuthMode.PERSONAL_ACCESS_TOKEN;
    }
}
