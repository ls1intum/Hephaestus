package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountMembershipQueryAdapterTest extends BaseUnitTest {
    @Test
    void shouldReturnNoMembershipsWhenAnAccountHasNotJoinedAWorkspace() {
        var memberships = mock(MembershipRepository.class);
        when(memberships.findByAccount_Id(42L)).thenReturn(List.of());
        assertThat(new AccountMembershipQueryAdapter(memberships, mock(AccountDirectory.class))
                        .membershipsForAccount(42L))
                .isEmpty();
    }

    @Test
    void shouldPropagateDatabaseFailureWhenAccountMembershipsAreRead() {
        var memberships = mock(MembershipRepository.class);
        when(memberships.findByAccount_Id(42L)).thenThrow(new IllegalStateException("Database unavailable"));
        assertThatThrownBy(() -> new AccountMembershipQueryAdapter(memberships, mock(AccountDirectory.class))
                        .membershipsForAccount(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");
    }
}
