package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adapter that bridges the gitprovider module's ScopeIdResolver SPI to the workspace domain.
 * <p>
 * This adapter resolves scope IDs (workspace IDs) using multiple strategies:
 * <ol>
 *   <li>By organization login - for organization-owned repositories</li>
 *   <li>By repository nameWithOwner - for personal repositories and fallback</li>
 * </ol>
 *
 * <h2>Personal Repository Support</h2>
 * Personal repositories (owned by GitHub users, not organizations) have no organization entity.
 * The {@link #findScopeIdByRepositoryName(String)} method enables activity tracking for these
 * repositories by looking up the workspace that monitors them.
 */
@Component
public class WorkspaceContextResolverAdapter implements ScopeIdResolver {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceContextResolverAdapter(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<Long> findScopeIdByOrgLogin(String organizationLogin) {
        // First try to find by linked organization login
        return workspaceRepository
            .findByOrganization_Login(organizationLogin)
            .or(() -> workspaceRepository.findByAccountLoginIgnoreCase(organizationLogin))
            .map(Workspace::getId);
    }

    @Override
    public Optional<Long> findScopeIdByRepositoryName(String repositoryNameWithOwner) {
        // Find workspace by monitored repository nameWithOwner
        // This supports personal repos and serves as fallback for org repos
        return workspaceRepository
            .findByRepositoriesToMonitor_NameWithOwner(repositoryNameWithOwner)
            .map(Workspace::getId);
    }
}
