package de.tum.cit.aet.hephaestus.integration.outline.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Pins the provider-scoped, membership-gated resolution chain for Outline document authors: an author
 * only ever resolves to the workspace member their linked account belongs to IN THAT WORKSPACE — never
 * login-only, never across workspaces. Mirrors the Slack mentor resolver contract.
 */
class OutlineIdentityResolverTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;
    private static final long PROVIDER_ID = 17L;
    private static final long ACCOUNT_ID = 7L;
    private static final String SERVER_URL = "https://wiki.example.com";
    private static final String TEAM_ID = "9ff8ee7d-team";
    private static final String SUBJECT = "0aa1bb2c-user";

    @Mock
    private GitProviderRegistry gitProviderRegistry;

    @Mock
    private AccountIdentityQuery accountIdentityQuery;

    @Mock
    private AccountWorkspaceMembershipQuery workspaceMembershipQuery;

    @Mock
    private UserRepository userRepository;

    private OutlineIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OutlineIdentityResolver(
            gitProviderRegistry,
            accountIdentityQuery,
            workspaceMembershipQuery,
            userRepository
        );
        lenient().when(gitProviderRegistry.resolveProviderId("OUTLINE", SERVER_URL)).thenReturn(PROVIDER_ID);
    }

    private static AccountIdentityQuery.IdentityLinkView link(String subject, String login) {
        return new AccountIdentityQuery.IdentityLinkView(1L, 2L, subject, login, null, null, null, null, null);
    }

    private static AccountWorkspaceMembershipQuery.WorkspaceMembershipView membership(long workspaceId) {
        return new AccountWorkspaceMembershipQuery.WorkspaceMembershipView(workspaceId, "ws", "Workspace", "MEMBER");
    }

    @Test
    @DisplayName("a linked author with membership in the workspace resolves to their member id")
    void happyPath_resolvesMemberId() {
        when(accountIdentityQuery.resolveAccountId(PROVIDER_ID, SUBJECT, TEAM_ID)).thenReturn(Optional.of(ACCOUNT_ID));
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(link("gh-123", "octocat")));
        when(workspaceMembershipQuery.membershipsForLogins(Set.of("octocat"))).thenReturn(
            List.of(membership(WORKSPACE_ID))
        );
        User user = new User();
        user.setId(555L);
        when(userRepository.findByLogin("octocat")).thenReturn(Optional.of(user));

        Optional<Long> memberId = resolver.resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, SUBJECT);

        assertThat(memberId).contains(555L);
        // The identity is keyed by the nOAuth-safe (provider, subject, team) triple.
        verify(accountIdentityQuery).resolveAccountId(PROVIDER_ID, SUBJECT, TEAM_ID);
    }

    @Test
    @DisplayName("an unknown / unlinked Outline subject resolves to empty")
    void unknownSubject_resolvesEmpty() {
        when(accountIdentityQuery.resolveAccountId(PROVIDER_ID, SUBJECT, TEAM_ID)).thenReturn(Optional.empty());

        assertThat(resolver.resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, SUBJECT)).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("membership in a DIFFERENT workspace is filtered out — never a cross-workspace attribution")
    void crossWorkspaceMembership_isFiltered() {
        when(accountIdentityQuery.resolveAccountId(PROVIDER_ID, SUBJECT, TEAM_ID)).thenReturn(Optional.of(ACCOUNT_ID));
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(link("gh-123", "octocat")));
        when(workspaceMembershipQuery.membershipsForLogins(Set.of("octocat"))).thenReturn(
            List.of(membership(WORKSPACE_ID + 1))
        );

        assertThat(resolver.resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, SUBJECT)).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("the Outline link's own display-name login drops out; the SCM login with membership wins")
    void picksTheScmLoginWithMembership() {
        when(accountIdentityQuery.resolveAccountId(PROVIDER_ID, SUBJECT, TEAM_ID)).thenReturn(Optional.of(ACCOUNT_ID));
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(
            List.of(link(SUBJECT, "Ada Lovelace"), link("gh-123", "octocat"))
        );
        when(workspaceMembershipQuery.membershipsForLogins(Set.of("Ada Lovelace"))).thenReturn(List.of());
        when(workspaceMembershipQuery.membershipsForLogins(Set.of("octocat"))).thenReturn(
            List.of(membership(WORKSPACE_ID))
        );

        assertThat(resolver.resolveDeveloperLogin(WORKSPACE_ID, SERVER_URL, TEAM_ID, SUBJECT)).contains("octocat");
    }

    @Test
    @DisplayName("blank subject or server URL short-circuits to empty without touching the registry")
    void blankInputs_shortCircuit() {
        assertThat(resolver.resolveMemberId(WORKSPACE_ID, SERVER_URL, TEAM_ID, " ")).isEmpty();
        assertThat(resolver.resolveMemberId(WORKSPACE_ID, "", TEAM_ID, SUBJECT)).isEmpty();
        verifyNoInteractions(gitProviderRegistry);
        verify(accountIdentityQuery, org.mockito.Mockito.never()).resolveAccountId(any(), any(), any());
    }
}
