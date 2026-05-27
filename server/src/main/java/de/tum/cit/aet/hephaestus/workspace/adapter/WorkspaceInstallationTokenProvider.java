package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.AuthMode;
import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationTokenProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that exposes the {@link InstallationTokenProvider} SPI on top of the
 * Connection registry. Every accessor pulls live state from the
 * {@link ConnectionService}; the {@link WorkspaceRepository} stays in the constructor
 * for one query — workspace lifecycle status — which the registry doesn't replicate.
 */
@Component
public class WorkspaceInstallationTokenProvider implements InstallationTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInstallationTokenProvider.class);

    private final WorkspaceRepository workspaceRepository;
    private final ConnectionService connectionService;

    public WorkspaceInstallationTokenProvider(
        WorkspaceRepository workspaceRepository,
        ConnectionService connectionService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.connectionService = connectionService;
    }

    @Override
    public Optional<Long> getInstallationId(Long scopeId) {
        return connectionService
            .findActiveGitHubAppConfig(scopeId)
            .map(ConnectionConfig.GitHubAppConfig::installationId);
    }

    @Override
    public Optional<String> getPersonalAccessToken(Long scopeId) {
        // Prefer GITHUB (PAT mode) → GITLAB. Pure App-mode workspaces have no
        // bearer credential blob; the caller must use the App-token mint path.
        return connectionService
            .findActiveBearerToken(scopeId, IntegrationKind.GITHUB)
            .or(() -> connectionService.findActiveBearerToken(scopeId, IntegrationKind.GITLAB))
            .map(b -> b.token())
            .filter(token -> token != null && !token.isBlank());
    }

    @Override
    public AuthMode getAuthMode(Long scopeId) {
        var kind = connectionService.findActiveProviderKind(scopeId);
        if (kind.isEmpty()) {
            log.warn("Defaulted to GITHUB_APP auth mode: reason=noActiveSCMConnection, scopeId={}", scopeId);
            return AuthMode.GITHUB_APP;
        }
        return switch (kind.get()) {
            case GITHUB -> connectionService.findActiveGitHubAppConfig(scopeId).isPresent()
                ? AuthMode.GITHUB_APP
                : AuthMode.PERSONAL_ACCESS_TOKEN;
            case GITLAB -> AuthMode.PERSONAL_ACCESS_TOKEN;
            case SLACK, OUTLINE -> {
                log.warn("Non-SCM provider for scope {}: kind={}; defaulting to GITHUB_APP", scopeId, kind.get());
                yield AuthMode.GITHUB_APP;
            }
        };
    }

    @Override
    public boolean isScopeActive(Long scopeId) {
        return workspaceRepository
            .findById(scopeId)
            .map(ws -> ws.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .orElse(false);
    }

    @Override
    public Optional<String> getServerUrl(Long scopeId) {
        return connectionService
            .findActiveGitLabConfig(scopeId)
            .map(ConnectionConfig.GitLabConfig::serverUrl)
            .or(() ->
                connectionService.findActiveGitHubAppConfig(scopeId).map(ConnectionConfig.GitHubAppConfig::serverUrl)
            )
            .or(() ->
                connectionService.findActiveGitHubPatConfig(scopeId).map(ConnectionConfig.GitHubPatConfig::serverUrl)
            )
            .filter(url -> url != null && !url.isBlank());
    }
}
