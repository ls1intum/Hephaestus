package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitLabWorkspaceLinkService {

    private static final Logger log = LoggerFactory.getLogger(GitLabWorkspaceLinkService.class);

    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;

    public GitLabWorkspaceLinkService(
        WorkspaceRepository workspaceRepository,
        OrganizationRepository organizationRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public void link(Workspace workspace) {
        if (
            workspace.getOrganization() != null ||
            workspace.getAccountLogin() == null ||
            workspace.getAccountLogin().isBlank()
        ) {
            return;
        }
        organizationRepository
            .findByLoginIgnoreCaseAndProvider_Type(workspace.getAccountLogin(), IdentityProviderType.GITLAB)
            .ifPresent(org -> {
                if (
                    workspaceRepository.existsByOrganizationId(org.getId()) &&
                    !workspaceRepository.existsByIdAndOrganizationId(workspace.getId(), org.getId())
                ) {
                    log.warn(
                        "Organization already linked to another workspace: orgId={}, workspaceId={}",
                        org.getId(),
                        workspace.getId()
                    );
                    return;
                }
                workspaceRepository
                    .findById(workspace.getId())
                    .filter(current -> current.getOrganization() == null)
                    .ifPresent(current -> {
                        current.setOrganization(org);
                        workspaceRepository.save(current);
                        workspace.setOrganization(org);
                        log.info(
                            "Linked organization to workspace: orgId={}, workspaceId={}",
                            org.getId(),
                            current.getId()
                        );
                    });
            });
    }
}
