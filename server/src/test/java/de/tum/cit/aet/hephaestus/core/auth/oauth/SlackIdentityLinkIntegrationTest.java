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
 * Slice 3 — authenticated Slack identity link, against a REAL Postgres. Drives {@link AccountProvisioningService}
 * with a SIMULATED "Sign in with Slack" id_token (a principal carrying {@code sub}=U1 + the verified
 * {@code https://slack.com/team_id}=T1 claim), which is exactly what the OIDC callback hands the provisioner —
 * so the full link path is exercised without a live Slack OAuth round-trip (that is LIVE-only).
 *
 * <p>Asserts the two S3 invariants: (1) linking Slack while signed in attaches exactly one
 * {@code IdentityLink(SLACK, U1, T1) MANUAL_LINK} to the SAME account, creating no new account; (2) an
 * unauthenticated link start (no bound account) fails closed and creates no orphan account.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class SlackIdentityLinkIntegrationTest {

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
    void linkingSlackAttachesExactlyOneSlackIdentityToTheSameAccount() {
        seedProvider("github-slacklink", LoginProvider.ProviderType.GITHUB);
        seedProvider("slack-link", LoginProvider.ProviderType.SLACK);

        // A developer signs in with GitHub → their base account.
        Account github = service
            .resolveOrProvision(
                "github-slacklink",
                "gh-1",
                githubPrincipal("gh-1", "dev@example.com", "octocat"),
                AuthIntentCookie.Intent.login(null, null)
            )
            .account();
        long accountsAfterLogin = accountRepository.count();

        // While authenticated, they link "Sign in with Slack" (simulated id_token: sub=U1, team_id=T1).
        AccountProvisioningService.ProvisionResult linked = service.resolveOrProvision(
            "slack-link",
            "U1",
            slackPrincipal("U1", "T1", "dev@example.com"),
            AuthIntentCookie.Intent.link(github.getId(), "/settings")
        );

        assertThat(linked.identityLinked()).isTrue();
        assertThat(linked.account().getId()).isEqualTo(github.getId());
        // The link attached to the existing account — it did NOT JIT a new one.
        assertThat(accountRepository.count()).isEqualTo(accountsAfterLogin);

        // Exactly one SLACK identity link (subject U1, team T1, MANUAL_LINK) now hangs off the SAME account.
        List<IdentityLink> links = identityLinkRepository.findActiveByAccountId(github.getId());
        assertThat(links).extracting(IdentityLink::getSubject).containsExactlyInAnyOrder("gh-1", "U1");
        IdentityLink slack = links
            .stream()
            .filter(l -> l.getSubject().equals("U1"))
            .findFirst()
            .orElseThrow();
        assertThat(slack.getTeamId()).isEqualTo("T1");
        assertThat(slack.getLinkedVia()).isEqualTo(IdentityLink.LinkedVia.MANUAL_LINK);
    }

    @Test
    void unauthenticatedSlackLinkStartCreatesNoAccount() {
        seedProvider("slack-noauth", LoginProvider.ProviderType.SLACK);
        long before = accountRepository.count();

        // A LINK-mode Slack callback with no authenticated account binding must fail closed — never an orphan.
        assertThatThrownBy(() ->
            service.resolveOrProvision(
                "slack-noauth",
                "U1",
                slackPrincipal("U1", "T1", "dev@example.com"),
                AuthIntentCookie.Intent.link(null, "/settings")
            )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(accountRepository.count()).isEqualTo(before);
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
        provider.setScopes(type == LoginProvider.ProviderType.SLACK ? "openid profile email" : "read:user");
        loginProviderRepository.save(provider);
    }

    private static OAuth2User githubPrincipal(String subject, String email, String login) {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", subject, "login", login, "email", email, "email_verified", true),
            "id"
        );
    }

    private static OAuth2User slackPrincipal(String subject, String teamId, String email) {
        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of(
                "sub",
                subject,
                "https://slack.com/team_id",
                teamId,
                "name",
                "Slack Dev",
                "email",
                email,
                "email_verified",
                true
            ),
            "sub"
        );
    }
}
