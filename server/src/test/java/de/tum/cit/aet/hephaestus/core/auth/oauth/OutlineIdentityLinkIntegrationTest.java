package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProvider;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProviderRepository;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Authenticated Outline identity link, against a REAL Postgres. Drives {@link AccountProvisioningService}
 * with the principal {@code OutlineAuthInfoUserService} builds from {@code POST /api/auth.info} (the
 * immutable user UUID as {@code id}, the Outline team UUID flattened onto {@code team_id}) — exactly
 * what the OAuth2 callback hands the provisioner, so the full link path is exercised without a live
 * Outline round-trip (that is LIVE-only).
 *
 * <p>Asserts the three invariants of a link-only provider: (1) linking Outline while signed in attaches
 * exactly one {@code IdentityLink(OUTLINE, user-uuid, team-uuid) MANUAL_LINK} to the SAME account and
 * creates none; (2) a login-mode Outline callback is refused ({@code link_requires_auth}) — Outline can
 * never be a sign-in button — as is a link with no authenticated account binding, neither leaving an
 * orphan account behind; (3) an Outline identity already linked to a DIFFERENT account cannot be
 * re-bound ({@code identity_already_linked}) — that would be account takeover.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class OutlineIdentityLinkIntegrationTest {

    /** Outline's subject is the immutable user UUID; the tenant key is the team UUID. */
    private static final String OUTLINE_USER = "0aa1bb2c-user";
    private static final String OUTLINE_TEAM = "9ff8ee7d-team";

    @Autowired
    private AccountProvisioningService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdentityLinkRepository identityLinkRepository;

    @Autowired
    private LoginProviderRepository loginProviderRepository;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @Test
    void linkingOutlineAttachesExactlyOneOutlineIdentityToTheSameAccount() {
        seedProvider("github-outlinelink", LoginProvider.ProviderType.GITHUB);
        seedProvider("outline-link", LoginProvider.ProviderType.OUTLINE);

        Account github = service
            .resolveOrProvision(
                "github-outlinelink",
                "gh-2",
                githubPrincipal("gh-2", "dev@example.com", "octocat"),
                AuthIntentCookie.Intent.login(null, null)
            )
            .account();
        long accountsAfterLogin = accountRepository.count();

        // While authenticated, they link their wiki identity (simulated auth.info principal).
        AccountProvisioningService.ProvisionResult linked = service.resolveOrProvision(
            "outline-link",
            OUTLINE_USER,
            outlinePrincipal(OUTLINE_USER, OUTLINE_TEAM, "dev@example.com"),
            AuthIntentCookie.Intent.link(github.getId(), "/settings")
        );

        assertThat(linked.identityLinked()).isTrue();
        assertThat(linked.account().getId()).isEqualTo(github.getId());
        // The link attached to the existing account — it did NOT JIT a new one.
        assertThat(accountRepository.count()).isEqualTo(accountsAfterLogin);

        List<IdentityLink> links = identityLinkRepository.findActiveByAccountId(github.getId());
        assertThat(links).extracting(IdentityLink::getSubject).containsExactlyInAnyOrder("gh-2", OUTLINE_USER);
        IdentityLink outline = links
            .stream()
            .filter(l -> l.getSubject().equals(OUTLINE_USER))
            .findFirst()
            .orElseThrow();
        // The tenant key is mandatory: an Outline user UUID is only unique within its team.
        assertThat(outline.getTeamId()).isEqualTo(OUTLINE_TEAM);
        assertThat(outline.getLinkedVia()).isEqualTo(IdentityLink.LinkedVia.MANUAL_LINK);
    }

    @Test
    void outlineLoginModeIsRefusedBecauseTheProviderIsLinkOnly() {
        seedProvider("outline-loginmode", LoginProvider.ProviderType.OUTLINE);
        long before = accountRepository.count();

        // LOGIN mode on a link-only provider — the success handler maps this to /auth/error?code=link_requires_auth.
        assertThatThrownBy(() ->
            service.resolveOrProvision(
                "outline-loginmode",
                OUTLINE_USER,
                outlinePrincipal(OUTLINE_USER, OUTLINE_TEAM, "dev@example.com"),
                AuthIntentCookie.Intent.login(null, null)
            )
        ).isInstanceOf(LinkOnlyProviderLoginException.class);

        assertThat(accountRepository.count()).isEqualTo(before);
    }

    @Test
    void unauthenticatedOutlineLinkStartCreatesNoAccount() {
        seedProvider("outline-noauth", LoginProvider.ProviderType.OUTLINE);
        long before = accountRepository.count();

        // A LINK-mode callback with no authenticated account binding must fail closed — never an orphan.
        assertThatThrownBy(() ->
            service.resolveOrProvision(
                "outline-noauth",
                OUTLINE_USER,
                outlinePrincipal(OUTLINE_USER, OUTLINE_TEAM, "dev@example.com"),
                AuthIntentCookie.Intent.link(null, "/settings")
            )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(accountRepository.count()).isEqualTo(before);
    }

    @Test
    void relinkingAnOutlineIdentityOwnedByAnotherAccountIsRefused() {
        seedProvider("github-outlineconflict-a", LoginProvider.ProviderType.GITHUB);
        seedProvider("github-outlineconflict-b", LoginProvider.ProviderType.GITHUB);
        seedProvider("outline-conflict", LoginProvider.ProviderType.OUTLINE);

        Account owner = service
            .resolveOrProvision(
                "github-outlineconflict-a",
                "gh-owner",
                githubPrincipal("gh-owner", "owner@example.com", "owner"),
                AuthIntentCookie.Intent.login(null, null)
            )
            .account();
        service.resolveOrProvision(
            "outline-conflict",
            OUTLINE_USER,
            outlinePrincipal(OUTLINE_USER, OUTLINE_TEAM, "owner@example.com"),
            AuthIntentCookie.Intent.link(owner.getId(), "/settings")
        );

        Account attacker = service
            .resolveOrProvision(
                "github-outlineconflict-b",
                "gh-attacker",
                githubPrincipal("gh-attacker", "attacker@example.com", "attacker"),
                AuthIntentCookie.Intent.login(null, null)
            )
            .account();

        // The SAME (OUTLINE, user, team) identity, linked from a different session: rebinding it would
        // hand the attacker the owner's wiki identity. Mapped to /auth/error?code=identity_already_linked.
        assertThatThrownBy(() ->
            service.resolveOrProvision(
                "outline-conflict",
                OUTLINE_USER,
                outlinePrincipal(OUTLINE_USER, OUTLINE_TEAM, "attacker@example.com"),
                AuthIntentCookie.Intent.link(attacker.getId(), "/settings")
            )
        ).isInstanceOf(AccountLinkConflictException.class);

        // The link still hangs off the original owner, and the attacker gained nothing.
        assertThat(identityLinkRepository.findActiveByAccountId(attacker.getId()))
            .extracting(IdentityLink::getSubject)
            .containsExactly("gh-attacker");
        assertThat(identityLinkRepository.findActiveByAccountId(owner.getId()))
            .extracting(IdentityLink::getSubject)
            .contains(OUTLINE_USER);
    }

    private void seedProvider(String registrationId, LoginProvider.ProviderType type) {
        LoginProvider provider = new LoginProvider();
        provider.setRegistrationId(registrationId);
        provider.setType(type);
        provider.setDisplayName(registrationId);
        // A baseUrl unique per registrationId keeps uq_login_provider_type_base_url + the git_provider
        // (type, server_url) key collision-free across the tests in this class.
        provider.setBaseUrl("https://" + registrationId + ".example");
        provider.setClientId("test-client-id");
        provider.setClientSecret("test-client-secret");
        provider.setScopes(type == LoginProvider.ProviderType.OUTLINE ? "read" : "read:user");
        loginProviderRepository.save(provider);
    }

    private static OAuth2User githubPrincipal(String subject, String email, String login) {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", subject, "login", login, "email", email, "email_verified", true),
            "id"
        );
    }

    /** The principal {@code OutlineAuthInfoUserService} flattens out of {@code auth.info}. */
    private static OAuth2User outlinePrincipal(String subject, String teamId, String email) {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", subject, "team_id", teamId, "team_name", "Acme", "name", "Ada Lovelace", "email", email),
            "id"
        );
    }
}
