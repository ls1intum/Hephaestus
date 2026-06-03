package de.tum.cit.aet.hephaestus.core.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code DELETE /user/identities/{id}} — unlinking a federated identity from the current account.
 *
 * <p>Uses the <b>real</b> {@code RevocationAwareJwtDecoder} + a genuine ES256 cookie-JWT (no mock
 * decoder), mirroring {@code AccountControllerIntegrationTest}. The bearer header path skips CSRF
 * (a pure bearer request carries no auth cookie), so these calls authenticate by token alone.
 *
 * <p>Covers the two safety rules every account-linking UI needs: ownership (you can only unlink your
 * own identity) and last-identity lockout prevention (you cannot remove your only sign-in method).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class AccountUnlinkIdentityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdentityLinkRepository identityLinkRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private HephaestusJwtIssuer jwtIssuer;

    @Autowired
    private JwtPrincipalFactory principalFactory;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        var postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }

    @Test
    void unlinkSecondaryIdentityDisablesItAndKeepsTheRest() {
        Account account = accountRepository.save(new Account("Two-provider User"));
        IdentityLink github = seedLink(account, GitProviderType.GITHUB, "https://github.com", "gh-1", "octocat");
        IdentityLink gitlab = seedLink(account, GitProviderType.GITLAB, "https://gitlab.lrz.de", "gl-1", "gluser");
        String token = tokenFor(account);

        webTestClient
            .delete()
            .uri("/user/identities/{id}", github.getId())
            .headers(h -> h.setBearerAuth(token))
            .exchange()
            .expectStatus()
            .isNoContent();

        var remaining = identityLinkRepository.findActiveByAccountId(account.getId());
        assertThat(remaining).extracting(IdentityLink::getId).containsExactly(gitlab.getId());
    }

    @Test
    void cannotUnlinkTheOnlyRemainingIdentity() {
        Account account = accountRepository.save(new Account("Single-provider User"));
        IdentityLink only = seedLink(account, GitProviderType.GITHUB, "https://github.com", "gh-only", "soloist");
        String token = tokenFor(account);

        webTestClient
            .delete()
            .uri("/user/identities/{id}", only.getId())
            .headers(h -> h.setBearerAuth(token))
            .exchange()
            .expectStatus()
            .isEqualTo(409);

        assertThat(identityLinkRepository.findActiveByAccountId(account.getId())).hasSize(1);
    }

    @Test
    void cannotUnlinkAnotherAccountsIdentity() {
        Account me = accountRepository.save(new Account("Me"));
        seedLink(me, GitProviderType.GITHUB, "https://github.com", "gh-me", "me");
        seedLink(me, GitProviderType.GITLAB, "https://gitlab.lrz.de", "gl-me", "me-gl");
        String myToken = tokenFor(me);

        Account other = accountRepository.save(new Account("Other"));
        IdentityLink otherGithub = seedLink(other, GitProviderType.GITHUB, "https://github.com", "gh-other", "other");

        webTestClient
            .delete()
            .uri("/user/identities/{id}", otherGithub.getId())
            .headers(h -> h.setBearerAuth(myToken))
            .exchange()
            .expectStatus()
            .isNotFound();

        assertThat(identityLinkRepository.findActiveByAccountId(other.getId()))
            .extracting(IdentityLink::getId)
            .containsExactly(otherGithub.getId());
    }

    @Test
    void unlinkingAnUnknownIdentityIs404() {
        Account account = accountRepository.save(new Account("User"));
        seedLink(account, GitProviderType.GITHUB, "https://github.com", "gh-x", "x");
        seedLink(account, GitProviderType.GITLAB, "https://gitlab.lrz.de", "gl-x", "x-gl");
        String token = tokenFor(account);

        webTestClient
            .delete()
            .uri("/user/identities/{id}", 9_999_999L)
            .headers(h -> h.setBearerAuth(token))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    private IdentityLink seedLink(
        Account account,
        GitProviderType type,
        String serverUrl,
        String subject,
        String login
    ) {
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(type, serverUrl)
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(type, serverUrl)));
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProviderId(provider.getId());
        link.setSubject(subject);
        link.setUsernameAtSignup(login);
        link.setDisplayName(login);
        return identityLinkRepository.save(link);
    }

    private String tokenFor(Account account) {
        return jwtIssuer.issue(principalFactory.forAccount(account), null, null).value();
    }
}
