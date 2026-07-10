package de.tum.cit.aet.hephaestus.core.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-Postgres proof that {@code existsActiveFeatureForProviderSubject} is provider-scoped — closing
 * the cross-provider feature-flag leak the login-only lookup had. The unit test
 * ({@code AccountRoleQueryServiceTest}) stubs the repository, so only a real query can prove the
 * {@code (gitProviderId, subject)} join actually isolates identical usernames/subjects across SCM
 * instances. Two people can share a username (and even a numeric subject) on two providers
 * ({@code uk_user_provider_login}); one holding a flag must NOT grant it to the other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class AccountFeatureRepositoryIntegrationTest {

    private static final String ROLE = "run_practice_review";
    private static final long PROVIDER_A = 100L;
    private static final long PROVIDER_B = 200L;
    private static final String SUBJECT = "583231"; // same numeric subject on both providers

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdentityLinkRepository identityLinkRepository;

    @Autowired
    private AccountFeatureRepository accountFeatureRepository;

    @Autowired
    private DatabaseTestUtils databaseTestUtils;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @BeforeEach
    void cleanSlate() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void featureFlagIsScopedToTheProviderItWasGrantedOn() {
        Account withFlag = accountRepository.save(new Account("Octo (provider A)"));
        Account withoutFlag = accountRepository.save(new Account("Octo (provider B)"));
        accountFeatureRepository.save(new AccountFeature(withFlag.getId(), ROLE));
        // Same subject AND same username on two different providers — two different people.
        link(withFlag, PROVIDER_A, SUBJECT);
        link(withoutFlag, PROVIDER_B, SUBJECT);

        // Granted-on provider resolves true...
        assertThat(accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_A, SUBJECT, ROLE)).isTrue();
        // ...the other provider's identity with the SAME subject does NOT (the leak is closed).
        assertThat(accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_B, SUBJECT, ROLE)).isFalse();
        // A different flag, and an unknown provider, both deny.
        assertThat(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_A, SUBJECT, "mentor_access")
        ).isFalse();
        assertThat(accountFeatureRepository.existsActiveFeatureForProviderSubject(999L, SUBJECT, ROLE)).isFalse();
    }

    @Test
    void disabledIdentityLinkDoesNotCarryTheFlag() {
        Account account = accountRepository.save(new Account("Disabled-link user"));
        accountFeatureRepository.save(new AccountFeature(account.getId(), ROLE));
        IdentityLink link = link(account, PROVIDER_A, SUBJECT);

        assertThat(accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_A, SUBJECT, ROLE)).isTrue();

        link.setDisabledAt(java.time.Instant.now());
        identityLinkRepository.save(link);

        assertThat(accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_A, SUBJECT, ROLE)).isFalse();
    }

    private IdentityLink link(Account account, long gitProviderId, String subject) {
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setProviderId(gitProviderId);
        link.setSubject(subject);
        link.setUsernameAtSignup("octocat"); // identical login on both providers — must not be the key
        return identityLinkRepository.save(link);
    }
}
