package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.account.AccountPreferencesQueryAdapter;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountMembershipQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AccountMembershipQueryIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private AccountMembershipQuery query;

    @Autowired
    private AccountPreferencesQueryAdapter preferences;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private IdentityRepository identities;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private MembershipRepository memberships;

    @Test
    void shouldReturnOneMembershipWhenAnAccountHasTwoProviderIdentities() {
        var account = accounts.saveAndFlush(new Account("Two identities"));
        account.setFeedbackDeliveryEnabled(false);
        var accountId = Objects.requireNonNull(account.getId());
        link(account, IdentityProviderType.GITHUB, "https://identity-github.example.com", "123");
        link(account, IdentityProviderType.GITLAB, "https://identity-gitlab.example.com", "456");
        var member = membership(account, "identity-owner");
        assertThat(query.membershipsForAccount(accountId)).singleElement().satisfies(view -> {
            assertThat(view.workspaceId()).isEqualTo(member.getWorkspace().getId());
            assertThat(view.accountId()).isEqualTo(accountId);
        });
        assertThat(preferences.preferencesForAccount(accountId))
                .get()
                .extracting(view -> view.practiceFeedbackDeliveryEnabled())
                .isEqualTo(false);
    }

    @Test
    void shouldExcludeLeftMembershipWhenAccountMembershipsAreRead() {
        var account = accounts.saveAndFlush(new Account("Former member"));
        var member = membership(account, "identity-left");
        member.setLeftAt(Instant.now());
        memberships.saveAndFlush(member);
        assertThat(query.membershipsForAccount(Objects.requireNonNull(account.getId())))
                .isEmpty();
        assertThat(memberships.findById(member.getId())).isPresent();
    }

    private void link(Account account, IdentityProviderType type, String origin, String key) {
        var provider = providers.saveAndFlush(new IdentityProvider(type, origin));
        var identity = new Identity();
        identity.setAccount(account);
        identity.setProviderId(Objects.requireNonNull(provider.getId()));
        identity.setProviderUserKey(key);
        identities.saveAndFlush(identity);
    }

    private Membership membership(Account account, String slug) {
        var workspace = new Workspace();
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName(slug);
        workspace.setAccountLogin(slug);
        workspace.setAccountType(AccountType.ORG);
        workspace = workspaces.saveAndFlush(workspace);
        var member = new Membership();
        member.setWorkspace(workspace);
        member.setId(new Membership.Id(member.getWorkspace().getId(), account.getId()));
        member.setId(new Membership.Id(workspace.getId(), account.getId()));
        return memberships.saveAndFlush(member);
    }
}
