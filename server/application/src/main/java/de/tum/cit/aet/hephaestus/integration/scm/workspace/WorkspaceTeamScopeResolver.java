package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// An absent synced organization means no teams; callers must never treat an empty scope as unrestricted.
@Service
public class WorkspaceTeamScopeResolver {

    private final WorkspaceOrganizationLookup organizations;

    public WorkspaceTeamScopeResolver(WorkspaceOrganizationLookup organizations) {
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceTeamScope> resolve(Workspace workspace) {
        if (workspace == null || workspace.getId() == null || workspace.getAccountLogin() == null) {
            return Optional.empty();
        }
        return organizations
                .find(workspace)
                .map(organization -> new WorkspaceTeamScope(
                        workspace.getAccountLogin(),
                        Objects.requireNonNull(organization.getProvider().getId())));
    }
}
