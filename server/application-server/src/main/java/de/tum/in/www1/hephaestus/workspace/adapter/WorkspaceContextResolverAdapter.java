package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.ScopeIdResolver;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceContextResolverAdapter implements ScopeIdResolver {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceContextResolverAdapter(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<Long> findScopeIdByOrgLogin(String organizationLogin) {
        // First try to find by linked organization login
        return workspaceRepository.findByOrganization_Login(organizationLogin)
            .or(() -> workspaceRepository.findByAccountLoginIgnoreCase(organizationLogin))
            .map(Workspace::getId);
    }
}
