package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.dto.AdminWorkspaceViewDTO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class WorkspaceAdminServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private AccountIdentityQuery accountIdentityQuery;

    @Test
    void shouldListImpersonatableOwnerAccountWhenOwnerHasSignedIn() {
        Workspace workspace = new Workspace();
        workspace.setId(12L);
        workspace.setWorkspaceSlug("acme");
        workspace.setDisplayName("Acme");
        workspace.setAccountLogin("acme-org");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);

        User owner = new User();
        owner.setId(34L);
        owner.setLogin("octocat");

        when(workspaceRepository.findAll()).thenReturn(List.of(workspace));
        when(membershipRepository.findUsersByWorkspaceIdAndRole(12L, WorkspaceRole.OWNER))
                .thenReturn(List.of(owner));
        when(accountIdentityQuery.resolveAccountIdForActor(34L)).thenReturn(Optional.of(56L));
        when(membershipRepository.countByWorkspace_Id(12L)).thenReturn(3L);

        WorkspaceAdminService service = new WorkspaceAdminService(
                workspaceRepository, membershipRepository, connectionService, accountIdentityQuery);

        AdminWorkspaceViewDTO view = service.listAll().getFirst();

        assertThat(view.ownerLogin()).isEqualTo("octocat");
        assertThat(view.ownerAccountId()).isEqualTo(56L);
    }
}
