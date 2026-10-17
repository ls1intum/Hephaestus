package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceOrganizationLookup {
    private final OrganizationRepository organizations;

    public Optional<Organization> find(Workspace workspace) {
        var id = workspace.getOrganizationId();
        return id == null ? Optional.empty() : organizations.findById(id);
    }
}
