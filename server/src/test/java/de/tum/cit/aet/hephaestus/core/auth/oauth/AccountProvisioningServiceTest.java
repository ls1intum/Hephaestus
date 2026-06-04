package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Pins the security-relevant provisioning behavior: verified-email stamping on JIT create, and
 * account-linking that binds to the current account and rejects cross-account collisions.
 */
class AccountProvisioningServiceTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final long PROVIDER_ID = 5L;

    private AccountRepository accountRepository;
    private IdentityLinkRepository identityLinkRepository;
    private VerifiedEmailResolver verifiedEmailResolver;
    private AccountJitCreator accountJitCreator;
    private AdminBootstrapPolicy adminBootstrapPolicy;
    private AccountProvisioningService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        identityLinkRepository = mock(IdentityLinkRepository.class);
        GitProviderRegistry gitProviderRegistry = mock(GitProviderRegistry.class);
        verifiedEmailResolver = mock(VerifiedEmailResolver.class);
        accountJitCreator = mock(AccountJitCreator.class);
        // Default: empty allowlist → no promotion (mock returns false for shouldPromote).
        adminBootstrapPolicy = mock(AdminBootstrapPolicy.class);
        lenient().when(gitProviderRegistry.resolveProviderId(any())).thenReturn(PROVIDER_ID);
        lenient()
            .when(accountRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0));
        // Default happy-path: the JIT creator persists and returns the supplied account.
        lenient()
            .when(accountJitCreator.create(any(), any()))
            .thenAnswer(inv -> inv.getArgument(0));
        service = new AccountProvisioningService(
            accountRepository,
            identityLinkRepository,
            gitProviderRegistry,
            verifiedEmailResolver,
            accountJitCreator,
            adminBootstrapPolicy,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void jitCreate_whenOnBootstrapAllowlist_promotesToAppAdmin() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        when(adminBootstrapPolicy.shouldPromote("github", "sub-1")).thenReturn(true);

        Account result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.login(null, null)
        );

        assertThat(result.getAppRole()).isEqualTo(Account.AppRole.APP_ADMIN);
    }

    @Test
    void jitCreate_whenNotOnBootstrapAllowlist_staysUser() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        // adminBootstrapPolicy.shouldPromote defaults to false.

        Account result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.login(null, null)
        );

        assertThat(result.getAppRole()).isEqualTo(Account.AppRole.USER);
    }

    private static OAuth2User principal() {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", "sub-1", "login", "octocat"),
            "id"
        );
    }

    private static IdentityLink linkOn(Account account) {
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProviderId(PROVIDER_ID);
        link.setSubject("sub-1");
        return link;
    }

    private static Account accountWithId(long id) {
        Account a = new Account("Ada");
        a.setId(id);
        return a;
    }

    @Test
    void jitCreate_verifiedEmail_stampsVerifiedAt() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );

        service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.login(null, null));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountJitCreator).create(saved.capture(), any());
        assertThat(saved.getValue().getPrimaryEmail()).isEqualTo("u@v.de");
        assertThat(saved.getValue().getPrimaryEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void jitCreate_unverifiedEmail_leavesVerifiedAtNull() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", false)
        );

        service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.login(null, null));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountJitCreator).create(saved.capture(), any());
        assertThat(saved.getValue().getPrimaryEmail()).isEqualTo("u@v.de");
        assertThat(saved.getValue().getPrimaryEmailVerifiedAt()).isNull();
    }

    @Test
    void jitCreate_whenConstraintLost_readsAfterConflictAndReturnsWinner() {
        Account winner = accountWithId(7L);
        // First lookup (pre-create) misses; the post-conflict re-read returns the winner's link.
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any()))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(linkOn(winner)));
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        when(accountJitCreator.create(any(), any())).thenThrow(
            new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates uq_identity_link_provider_subject_team"
            )
        );

        Account result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.login(null, null)
        );

        // Fail closed: return the concurrently-created winner, never a second orphan account.
        assertThat(result.getId()).isEqualTo(7L);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void jitCreate_whenConstraintLostButNoWinnerVisible_failsClosed() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        when(accountJitCreator.create(any(), any())).thenThrow(
            new org.springframework.dao.DataIntegrityViolationException("duplicate key")
        );

        assertThatThrownBy(() ->
            service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.login(null, null))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("lost the race");
    }

    @Test
    void link_bindsToCurrentAccount() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(accountRepository.findById(42L)).thenReturn(Optional.of(accountWithId(42L)));

        Account result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.link(42L, null)
        );

        assertThat(result.getId()).isEqualTo(42L);
        ArgumentCaptor<IdentityLink> saved = ArgumentCaptor.forClass(IdentityLink.class);
        verify(identityLinkRepository).save(saved.capture());
        assertThat(saved.getValue().getLinkedVia()).isEqualTo(IdentityLink.LinkedVia.MANUAL_LINK);
        assertThat(saved.getValue().getAccount().getId()).isEqualTo(42L);
        verify(accountRepository, never()).save(any()); // link attaches, never JIT-creates
    }

    @Test
    void link_rejectsWhenIdentityAlreadyLinkedElsewhere() {
        // The incoming identity is already an ACTIVE link on account 99; operator is linking into 42.
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.of(linkOn(accountWithId(99L)))
        );

        assertThatThrownBy(() ->
            service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.link(42L, null))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already linked to a different account");

        verify(identityLinkRepository, never()).save(any());
    }

    @Test
    void link_idempotentWhenAlreadyLinkedToSameAccount() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.of(linkOn(accountWithId(42L)))
        );

        Account result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.link(42L, null)
        );

        assertThat(result.getId()).isEqualTo(42L);
        verify(identityLinkRepository).touchLastLogin(any(), any());
        verify(identityLinkRepository, never()).save(any());
    }

    @Test
    void link_nullBinding_failsClosed() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.link(null, null))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("authenticated account binding");

        verify(accountRepository, never()).save(any());
    }
}
