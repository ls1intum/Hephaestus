package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationLinkService {

    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public void attachOrganization(Long workspaceId, Long installationId) {
        Workspace ws = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        Organization org = organizationRepository
            .findByInstallationId(installationId)
            .orElseThrow(() -> new IllegalStateException("No org for installation " + installationId));

        ws.setOrganization(org);
        workspaceRepository.save(ws);
    }

    @Transactional
    public void setAccountLoginOnly(Long workspaceId, String orgLogin) {
        Workspace ws = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        ws.setAccountLogin(orgLogin);
        workspaceRepository.save(ws);
    }
}
