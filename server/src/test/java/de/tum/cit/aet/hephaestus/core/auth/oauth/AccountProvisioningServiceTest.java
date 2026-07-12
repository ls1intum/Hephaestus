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
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProvider;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProviderRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.ExternalActorQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Pins the security-relevant provisioning behavior: verified-email stamping on JIT create, and
 * account-linking that binds to the current account and rejects cross-account collisions.
 */
@Tag("unit")
class AccountProvisioningServiceTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final long PROVIDER_ID = 5L;

    private AccountRepository accountRepository;
    private IdentityLinkRepository identityLinkRepository;
    private VerifiedEmailResolver verifiedEmailResolver;
    private AccountJitCreator accountJitCreator;
    private AdminBootstrapPolicy adminBootstrapPolicy;
    private LoginProviderRepository loginProviderRepository;
    private ExternalActorQuery externalActorQuery;
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
        loginProviderRepository = mock(LoginProviderRepository.class);
        externalActorQuery = mock(ExternalActorQuery.class);
        // Default: no synced SCM actor exists yet — the link stays unbound (the lazy path fills it later).
        lenient()
            .when(externalActorQuery.findExternalActorId(org.mockito.ArgumentMatchers.anyLong(), any(), any()))
            .thenReturn(Optional.empty());
        var githubProvider = new LoginProvider();
        githubProvider.setRegistrationId("github");
        githubProvider.setType(LoginProvider.ProviderType.GITHUB);
        githubProvider.setBaseUrl("https://github.com");
        lenient().when(loginProviderRepository.findByRegistrationId(any())).thenReturn(Optional.of(githubProvider));
        lenient().when(gitProviderRegistry.resolveProviderId(any(), any())).thenReturn(PROVIDER_ID);
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
            loginProviderRepository,
            verifiedEmailResolver,
            accountJitCreator,
            adminBootstrapPolicy,
            externalActorQuery,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void slackLoginMode_rejectedBecauseSlackIsLinkOnly() {
        useSlackProvider();

        assertThatThrownBy(() ->
            service.resolveOrProvision("slack", "U123", principal(), AuthIntentCookie.Intent.login(null, null))
        ).isInstanceOf(LinkOnlyProviderLoginException.class);

        verify(accountJitCreator, never()).create(any(), any());
        verify(identityLinkRepository, never()).save(any());
    }

    @Test
    void slackLink_withoutTeamId_failsClosed() {
        useSlackProvider();

        assertThatThrownBy(() ->
            service.resolveOrProvision("slack", "U123", principal(), AuthIntentCookie.Intent.link(42L, null))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("team_id");

        verify(identityLinkRepository, never()).save(any());
    }

    @Test
    void jitCreate_whenOnBootstrapAllowlist_promotesToAppAdmin() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        when(adminBootstrapPolicy.shouldPromote(eq("github"), eq("sub-1"), any())).thenReturn(true);

        var result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.login(null, null)
        );

        assertThat(result.account().getAppRole()).isEqualTo(Account.AppRole.APP_ADMIN);
        assertThat(result.identityLinked()).as("a JIT login is not an identity-link").isFalse();
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

        var result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.login(null, null)
        );

        assertThat(result.account().getAppRole()).isEqualTo(Account.AppRole.USER);
    }

    @Test
    void jitCreate_whenScmActorResolvable_bindsExternalActorIdOnTheLink() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        when(externalActorQuery.findExternalActorId(PROVIDER_ID, "sub-1", "octocat")).thenReturn(Optional.of(42L));

        service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.login(null, null));

        ArgumentCaptor<IdentityLink> link = ArgumentCaptor.forClass(IdentityLink.class);
        verify(accountJitCreator).create(any(), link.capture());
        assertThat(link.getValue().getExternalActorId()).isEqualTo(42L);
        // The eager bind never touches linked_via: a JIT link keeps its OAUTH_LOGIN default (CHECK constraint).
        assertThat(link.getValue().getLinkedVia()).isEqualTo(IdentityLink.LinkedVia.OAUTH_LOGIN);
    }

    @Test
    void jitCreate_whenNoScmActorSyncedYet_leavesExternalActorIdNull() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.empty()
        );
        when(verifiedEmailResolver.resolve(eq("github"), any())).thenReturn(
            new VerifiedEmailResolver.ResolvedEmail("u@v.de", true)
        );
        // externalActorQuery defaults to Optional.empty() — nothing synced for this identity yet.

        service.resolveOrProvision("github", "sub-1", principal(), AuthIntentCookie.Intent.login(null, null));

        ArgumentCaptor<IdentityLink> link = ArgumentCaptor.forClass(IdentityLink.class);
        verify(accountJitCreator).create(any(), link.capture());
        assertThat(link.getValue().getExternalActorId()).isNull();
    }

    private static OAuth2User principal() {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", "sub-1", "login", "octocat"),
            "id"
        );
    }

    private void useSlackProvider() {
        var slackProvider = new LoginProvider();
        slackProvider.setRegistrationId("slack");
        slackProvider.setType(LoginProvider.ProviderType.SLACK);
        slackProvider.setBaseUrl("https://slack.com");
        when(loginProviderRepository.findByRegistrationId("slack")).thenReturn(Optional.of(slackProvider));
    }

    private static IdentityLink linkOn(Account account) {
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setProviderId(PROVIDER_ID);
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
            new DataIntegrityViolationException("duplicate key value violates uq_identity_link_provider_subject_team")
        );

        var result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.login(null, null)
        );

        // Fail closed: return the concurrently-created winner, never a second orphan account.
        assertThat(result.account().getId()).isEqualTo(7L);
        assertThat(result.identityLinked()).isFalse();
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
        when(accountJitCreator.create(any(), any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

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

        var result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.link(42L, null)
        );

        assertThat(result.account().getId()).isEqualTo(42L);
        assertThat(result.identityLinked()).as("attaching a new identity is an identity-link").isTrue();
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
            .isInstanceOf(AccountLinkConflictException.class)
            .hasMessageContaining("already linked to a different account");

        verify(identityLinkRepository, never()).save(any());
    }

    @Test
    void link_idempotentWhenAlreadyLinkedToSameAccount() {
        when(identityLinkRepository.findActiveByProviderSubject(eq(PROVIDER_ID), eq("sub-1"), any())).thenReturn(
            Optional.of(linkOn(accountWithId(42L)))
        );

        var result = service.resolveOrProvision(
            "github",
            "sub-1",
            principal(),
            AuthIntentCookie.Intent.link(42L, null)
        );

        assertThat(result.account().getId()).isEqualTo(42L);
        // Re-affirming an already-linked identity persists NO new link, so it must NOT report a link
        // (the handler would otherwise audit a phantom IDENTITY_LINKED for what is really a login).
        assertThat(result.identityLinked()).isFalse();
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
