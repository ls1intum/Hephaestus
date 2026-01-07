package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceIdResolver;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceContextResolverAdapter implements WorkspaceIdResolver {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceContextResolverAdapter(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<Long> findWorkspaceIdByOrgLogin(String organizationLogin) {
        return workspaceRepository.findByOrganization_Login(organizationLogin).map(Workspace::getId);
    }
}
