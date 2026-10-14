package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.dto.AdminWorkspaceViewDTO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Instance-admin cross-tenant workspace listing (metadata only). The whole service is {@link
 * WorkspaceAgnostic} so the listing — including the per-workspace connection / membership lookups —
 * runs under a tenancy bypass and sees every workspace, not just the caller's. Access is gated
 * upstream by {@code hasAuthority('app_admin')} on {@link WorkspaceAdminController}.
 */
@Service
@WorkspaceAgnostic("Instance-admin overview lists every workspace across tenants (metadata only)")
public class WorkspaceAdminService {

    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;
    private final ConnectionService connectionService;
    private final AccountDirectory accounts;

    public WorkspaceAdminService(
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository,
            ConnectionService connectionService,
            AccountDirectory accounts) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.connectionService = connectionService;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<AdminWorkspaceViewDTO> listAll() {
        return workspaceRepository.findAll().stream().map(this::toView).toList();
    }

    private AdminWorkspaceViewDTO toView(Workspace ws) {
        IdentityProviderType providerType = connectionService
                .findActiveProviderKind(ws.getId())
                .map(IdentityProviderType::from)
                .orElse(null);
        var owner = membershipRepository.findAccountIdsByWorkspaceIdAndRole(ws.getId(), WorkspaceRole.OWNER).stream()
                .findFirst()
                .flatMap(accounts::find)
                .orElse(null);
        return new AdminWorkspaceViewDTO(
                ws.getId(),
                ws.getWorkspaceSlug(),
                ws.getDisplayName(),
                ws.getStatus(),
                ws.getAccountLogin(),
                providerType,
                owner != null ? owner.displayName() : null,
                membershipRepository.countByWorkspace_Id(ws.getId()),
                ws.getCreatedAt());
    }
}
