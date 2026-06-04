package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end validation of the break-glass first-admin path against real Postgres: the atomic,
 * self-disabling {@code promoteToFirstAdminIfNoneExists} SQL plus the token gate. A clean DB per test
 * makes the global {@code NOT EXISTS} gate deterministic. The token is configured here so the path is
 * enabled (it is disabled — 404 — when blank, covered by the unit test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GitHubIntegrationPostgresShutdown.class)
@TestPropertySource(properties = "hephaestus.auth.bootstrap-token=" + AccountBootstrapServiceIntegrationTest.TOKEN)
@Testcontainers
@Tag("integration")
class AccountBootstrapServiceIntegrationTest {

    static final String TOKEN = "bootstrap-break-glass-token-please-rotate";

    @Autowired
    private AccountBootstrapService bootstrapService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DatabaseTestUtils databaseTestUtils;

    @BeforeEach
    void cleanSlate() {
        databaseTestUtils.cleanDatabase();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    private Account persistUser(String name) {
        return accountRepository.save(new Account(name));
    }

    private Account persistAdmin(String name) {
        Account a = new Account(name);
        a.setAppRole(Account.AppRole.APP_ADMIN);
        return accountRepository.save(a);
    }

    @Test
    void promotesCallerWhenNoAdminExists() {
        Account user = persistUser("Hopeful");

        bootstrapService.bootstrapFirstAdmin(user.getId(), TOKEN);

        assertThat(accountRepository.findById(user.getId()).orElseThrow().getAppRole()).isEqualTo(
            Account.AppRole.APP_ADMIN
        );
    }

    @Test
    void selfDisablesOnceAnAdminExists() {
        persistAdmin("Existing Admin");
        Account user = persistUser("Late Hopeful");

        assertThatThrownBy(() -> bootstrapService.bootstrapFirstAdmin(user.getId(), TOKEN)).isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );
        assertThat(accountRepository.findById(user.getId()).orElseThrow().getAppRole()).isEqualTo(Account.AppRole.USER);
    }

    @Test
    void wrongTokenIsRejectedAndDoesNotPromote() {
        Account user = persistUser("Hopeful");

        assertThatThrownBy(() -> bootstrapService.bootstrapFirstAdmin(user.getId(), "wrong")).isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
        );
        assertThat(accountRepository.findById(user.getId()).orElseThrow().getAppRole()).isEqualTo(Account.AppRole.USER);
    }
}
