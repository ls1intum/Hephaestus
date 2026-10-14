package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.core.auth.spi.PeopleRegistry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.authorization.WorkspaceAccessService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MembershipServiceEligibilityTest extends BaseUnitTest {
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final AccountIdentityQuery identities = mock(AccountIdentityQuery.class);
    private final WorkspaceActorSelector actors = mock(WorkspaceActorSelector.class);
    private final ObjectProvider<PeopleRegistry> people = mock();
    private final AccountDirectory accounts = mock(AccountDirectory.class);
    private final MembershipService service = new MembershipService(
            memberships,
            mock(WorkspaceRepository.class),
            mock(ConfigAuditPort.class),
            mock(WorkspaceAccessService.class),
            people,
            identities,
            actors,
            Clock.systemUTC(),
            accounts,
            mock(GitProviderRegistry.class));

    @Test
    void shouldSelectUnclaimedHumanAccountsWhenTheirIdentityMatchesTheWorkspace() {
        var account = new Account("Developer");
        account.setId(100L);
        account.setStatus(Account.Status.UNCLAIMED);
        var member = new Membership();
        member.setId(new Membership.Id(1L, 100L));
        when(memberships.findByWorkspace_IdAndAccount_Id(1L, 100L)).thenReturn(Optional.of(member));
        when(accounts.find(100L))
                .thenReturn(Optional.of(
                        new AccountDirectory.AccountView(100L, account.getStatus(), "Developer", null, null, true)));
        when(actors.connectedProviderId(1L)).thenReturn(Optional.of(10L));
        when(identities.activeLinksForAccount(100L))
                .thenReturn(List.of(new AccountIdentityQuery.IdentityView(1L, 10L, "123", "")));
        assertThat(service.isPracticeReviewEligible(1L, 100L)).isTrue();
        member.setLeftAt(Instant.now());
        assertThat(service.isPracticeReviewEligible(1L, 100L)).isFalse();
    }

    @Test
    void shouldRefuseAccountsWhoseIdentityDoesNotMatchTheWorkspace() {
        var account = new Account("Developer");
        account.setId(100L);
        var member = new Membership();
        member.setId(new Membership.Id(1L, 100L));
        when(memberships.findByWorkspace_IdAndAccount_Id(1L, 100L)).thenReturn(Optional.of(member));
        when(accounts.find(100L))
                .thenReturn(Optional.of(
                        new AccountDirectory.AccountView(100L, account.getStatus(), "Developer", null, null, true)));
        when(actors.connectedProviderId(1L)).thenReturn(Optional.of(10L));
        when(identities.activeLinksForAccount(100L))
                .thenReturn(List.of(new AccountIdentityQuery.IdentityView(1L, 20L, "123", "")));
        assertThat(service.isPracticeReviewEligible(1L, 100L)).isFalse();
    }
}
