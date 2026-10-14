package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NativeAccountMembershipQueryTest extends BaseUnitTest {
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final AccountDirectory accounts = mock(AccountDirectory.class);
    private final AccountMembershipQueryAdapter query = new AccountMembershipQueryAdapter(memberships, accounts);

    @Test
    void shouldResolveAdministratorWhenAnActiveAccountHoldsTheRole() {
        var account = new Account("Administrator");
        account.setId(41L);
        var member = new Membership();
        member.setId(new Membership.Id(7L, account.getId()));
        member.setRole(Membership.WorkspaceRole.ADMIN);
        when(memberships.findByWorkspace_Id(7L)).thenReturn(List.of(member));
        when(accounts.find(41L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        41L, Account.Status.ACTIVE, "Administrator", null, null, true)));
        assertThat(query.administratorAccountIds(7L)).containsExactly(41L);
        assertThat(query.isAdministrator(7L, 41L)).isTrue();
        assertThat(query.isAdministrator(7L, 42L)).isFalse();
    }
}
