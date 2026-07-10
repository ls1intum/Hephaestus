package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery.IdentityLinkView;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.MockSecurityContextUtils;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Resolution of the current account's SCM user mirrors. The load-bearing security property: identities
 * are resolved <em>within their own provider</em> (by wired actor id, else by {@code (provider, login)}),
 * NEVER by login alone — {@code user} uniqueness is provider-scoped ({@code uk_user_provider_login}), so a
 * login-only match would union a different person's namesake (and their workspace memberships) into the
 * account. See the cross-provider isolation test.
 */
class CurrentAccountUsersTest extends BaseUnitTest {

    private static final long GITHUB = 1L;
    private static final long GITLAB = 2L;

    private AccountIdentityQuery accountIdentityQuery;
    private UserRepository userRepository;
    private CurrentAccountUsers currentAccountUsers;

    @BeforeEach
    void setUp() {
        accountIdentityQuery = mock(AccountIdentityQuery.class);
        userRepository = mock(UserRepository.class);
        currentAccountUsers = new CurrentAccountUsers(accountIdentityQuery, userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAccount(long accountId) {
        SecurityContextHolder.setContext(
            MockSecurityContextUtils.createSecurityContext("login", Long.toString(accountId), new String[0], "token")
        );
    }

    private static IdentityLinkView link(long providerId, String login, Long externalActorId) {
        return new IdentityLinkView(1L, providerId, "subject", login, "Display", null, null, externalActorId, null);
    }

    private static User user(long id, String login) {
        User u = new User();
        u.setLogin(login);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void resolvesEachIdentityWithinItsOwnProviderNeverByLoginAlone() {
        authenticateAccount(42L);
        // The account is linked ONLY to GitHub login "alice".
        when(accountIdentityQuery.activeLinksForAccount(42L)).thenReturn(List.of(link(GITHUB, "alice", null)));
        when(userRepository.findByLoginAndProviderId("alice", GITHUB)).thenReturn(Optional.of(user(100L, "alice")));
        // A DIFFERENT person also called "alice" exists on GitLab — the cross-provider namesake. If
        // resolution ever keyed on login alone, this stranger's user (and workspaces) would be unioned in.
        lenient()
            .when(userRepository.findByLoginAndProviderId("alice", GITLAB))
            .thenReturn(Optional.of(user(200L, "alice")));

        List<User> resolved = currentAccountUsers.resolve();

        assertThat(resolved).extracting(User::getId).containsExactly(100L);
        verify(userRepository).findByLoginAndProviderId("alice", GITHUB);
        // The GitLab namesake is never fetched — the account isn't linked to that provider.
        verify(userRepository, never()).findByLoginAndProviderId("alice", GITLAB);
    }

    @Test
    void prefersTheWiredExternalActorIdOverProviderLoginLookup() {
        authenticateAccount(7L);
        when(accountIdentityQuery.activeLinksForAccount(7L)).thenReturn(List.of(link(GITHUB, "bob", 500L)));
        when(userRepository.findById(500L)).thenReturn(Optional.of(user(500L, "bob")));

        List<User> resolved = currentAccountUsers.resolve();

        assertThat(resolved).extracting(User::getId).containsExactly(500L);
        verify(userRepository).findById(500L);
        verify(userRepository, never()).findByLoginAndProviderId(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void unionsAcrossAllLinkedIdentitiesDedupedByUserId() {
        authenticateAccount(9L);
        when(accountIdentityQuery.activeLinksForAccount(9L)).thenReturn(
            List.of(link(GITHUB, "carol", null), link(GITLAB, "carol-gl", null))
        );
        when(userRepository.findByLoginAndProviderId("carol", GITHUB)).thenReturn(Optional.of(user(11L, "carol")));
        when(userRepository.findByLoginAndProviderId("carol-gl", GITLAB)).thenReturn(
            Optional.of(user(22L, "carol-gl"))
        );

        assertThat(currentAccountUsers.resolve()).extracting(User::getId).containsExactly(11L, 22L);
    }

    @Test
    void fallsBackToSingleCurrentUserWhenTokenCarriesNoAccountId() {
        // No authentication → no account id (legacy token). Behaviour must be no worse than the previous
        // single-identity resolution.
        when(userRepository.getCurrentUser()).thenReturn(Optional.of(user(1L, "legacy")));

        assertThat(currentAccountUsers.resolve()).extracting(User::getId).containsExactly(1L);
        verify(accountIdentityQuery, never()).activeLinksForAccount(ArgumentMatchers.anyLong());
    }
}
