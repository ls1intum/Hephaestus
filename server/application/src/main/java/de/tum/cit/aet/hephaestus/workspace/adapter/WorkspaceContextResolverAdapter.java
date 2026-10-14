package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.integration.core.spi.ScopeIdResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Personal repositories have no organization row; resolve their workspace through repository monitors.
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
                .findByAccountLoginIgnoreCase(organizationLogin)
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
