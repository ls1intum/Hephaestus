package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipRosterServiceTest extends BaseUnitTest {
    @Test
    void shouldReturnTypedNotFoundWhenWorkspaceIsMissing() {
        var workspaces = mock(WorkspaceRepository.class);
        when(workspaces.findByIdForUpdate(17L)).thenReturn(Optional.empty());
        var roster =
                new MembershipRosterService(workspaces, mock(MembershipRepository.class), mock(AccountDirectory.class));
        assertThatThrownBy(() -> roster.ensureMember(17L, 23L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("workspace");
    }

    @Test
    void shouldReturnTypedNotFoundWhenAccountIsMissing() {
        var workspaces = mock(WorkspaceRepository.class);
        var memberships = mock(MembershipRepository.class);
        var accounts = mock(AccountDirectory.class);
        var workspace = new Workspace();
        when(workspaces.findByIdForUpdate(17L)).thenReturn(Optional.of(workspace));
        when(memberships.findByWorkspace_IdAndAccount_Id(17L, 23L)).thenReturn(Optional.empty());
        when(accounts.find(23L)).thenReturn(Optional.empty());
        var roster = new MembershipRosterService(workspaces, memberships, accounts);
        assertThatThrownBy(() -> roster.ensureMember(17L, 23L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("account");
    }
}
