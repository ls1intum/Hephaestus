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
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeMode;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private PracticeReviewCoverageService coverage;

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

    @Test
    void shouldPageOnlyCurrentViewableAccountsInDisplayNameOrder() {
        var first = accounts.saveAndFlush(new Account("Alpha developer"));
        var firstMember = membership(first, "bounded-account-page");
        var workspace = firstMember.getWorkspace();
        var second = accounts.saveAndFlush(new Account("Bravo developer"));
        var third = accounts.saveAndFlush(new Account("Charlie developer"));
        var deleting = accounts.saveAndFlush(new Account("Deleted developer"));
        deleting.setStatus(Account.Status.DELETING);
        accounts.saveAndFlush(deleting);
        var former = accounts.saveAndFlush(new Account("Former developer"));
        addMember(workspace, second, Membership.WorkspaceRole.MEMBER, null);
        addMember(workspace, third, Membership.WorkspaceRole.MEMBER, null);
        addMember(workspace, deleting, Membership.WorkspaceRole.MEMBER, null);
        addMember(workspace, former, Membership.WorkspaceRole.MEMBER, Instant.now());
        membership(accounts.saveAndFlush(new Account("Foreign developer")), "foreign-bounded-page");
        var page = memberships.findViewableAccountIds(workspace.getId(), PageRequest.of(1, 1));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).containsExactly(second.getId());
    }

    @Test
    void shouldRequireActiveCurrentAdministratorMembershipInTheRequestedWorkspace() {
        var active = accounts.saveAndFlush(new Account("Active administrator"));
        var member = membership(active, "bounded-administrators");
        var workspace = member.getWorkspace();
        member.setRole(Membership.WorkspaceRole.ADMIN);
        memberships.saveAndFlush(member);
        var suspended = accounts.saveAndFlush(new Account("Suspended administrator"));
        suspended.setStatus(Account.Status.SUSPENDED);
        accounts.saveAndFlush(suspended);
        addMember(workspace, suspended, Membership.WorkspaceRole.OWNER, null);
        var former = accounts.saveAndFlush(new Account("Former administrator"));
        addMember(workspace, former, Membership.WorkspaceRole.ADMIN, Instant.now());
        long id = Objects.requireNonNull(active.getId());
        assertThat(query.administratorAccountIds(workspace.getId())).containsExactly(id);
        assertThat(query.isAdministrator(workspace.getId(), id)).isTrue();
        assertThat(query.isAdministrator(workspace.getId(), Objects.requireNonNull(suspended.getId())))
                .isFalse();
        assertThat(query.isAdministrator(workspace.getId(), Objects.requireNonNull(former.getId())))
                .isFalse();
        assertThat(query.isAdministrator(Long.MAX_VALUE, id)).isFalse();
    }

    @Test
    void shouldSelectOnlyCurrentAccountsWithAnEnabledIdentityOnTheRequestedProvider() {
        var human = accounts.saveAndFlush(new Account("Eligible human"));
        var member = membership(human, "batch-review-eligibility");
        var provider = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITHUB, "https://batch-review.example.com"));
        var identity = new Identity();
        identity.setAccount(human);
        identity.setProviderId(Objects.requireNonNull(provider.getId()));
        identity.setProviderUserKey("batch-review-human");
        identities.saveAndFlush(identity);
        var unclaimed = accounts.saveAndFlush(new Account("Unclaimed human"));
        unclaimed.setStatus(Account.Status.UNCLAIMED);
        accounts.saveAndFlush(unclaimed);
        addMember(member.getWorkspace(), unclaimed, Membership.WorkspaceRole.MEMBER, null);
        var ids = List.of(Objects.requireNonNull(human.getId()), Objects.requireNonNull(unclaimed.getId()));
        assertThat(memberships.findEligibleAccountIds(member.getWorkspace().getId(), ids, provider.getId()))
                .containsExactly(human.getId());
        assertThat(memberships.findEligibleAccountIds(member.getWorkspace().getId(), ids, null))
                .containsExactlyInAnyOrderElementsOf(ids);
        identity.setDisabledAt(Instant.now());
        identities.saveAndFlush(identity);
        assertThat(memberships.findEligibleAccountIds(member.getWorkspace().getId(), ids, provider.getId()))
                .isEmpty();
    }

    @Test
    void shouldIncludeThenExcludeTheSelectedAccountWhenReviewCoverageChanges() {
        var human = accounts.saveAndFlush(new Account("Selected human"));
        var member = membership(human, "selected-account-coverage");
        var workspace = member.getWorkspace();
        long accountId = Objects.requireNonNull(human.getId());
        coverage.replace(
                workspace,
                new WorkspaceReviewScope(ReviewScopeMode.ALL, ReviewScopeMode.SELECTED, List.of(), List.of(accountId)));
        assertThat(coverage.scope(workspace).accountIds()).containsExactly(accountId);
        coverage.replace(
                workspace,
                new WorkspaceReviewScope(ReviewScopeMode.ALL, ReviewScopeMode.SELECTED, List.of(), List.of()));
        assertThat(coverage.scope(workspace).accountIds()).isEmpty();
    }

    private void addMember(
            Workspace workspace, Account account, Membership.WorkspaceRole role, @Nullable Instant leftAt) {
        var member = new Membership();
        member.setWorkspace(workspace);
        member.setId(new Membership.Id(workspace.getId(), account.getId()));
        member.setRole(role);
        member.setLeftAt(leftAt);
        memberships.saveAndFlush(member);
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
        member.setId(new Membership.Id(workspace.getId(), account.getId()));
        return memberships.saveAndFlush(member);
    }
}
