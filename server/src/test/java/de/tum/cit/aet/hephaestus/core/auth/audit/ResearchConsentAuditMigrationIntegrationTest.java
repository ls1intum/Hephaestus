package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.cit.aet.hephaestus.testconfig.DatabaseTestUtils;
import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import java.time.Instant;
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
 * S4 migration guard (changeset {@code 1782980500800-15}): the {@code ck_auth_event_event_type} CHECK
 * constraint must admit the new {@code RESEARCH_CONSENT_REVOKED} value against real Postgres. If the constraint
 * widening did not run, inserting the row raises a check violation and this test fails — proving the enum value
 * and its schema delta landed together (the {@code ddl-auto: validate} + boot gate covers the rest).
 *
 * <p>Deterministic (Testcontainers). Compile-verified here; the {@code @Tag("integration")} tier is run in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class ResearchConsentAuditMigrationIntegrationTest {

    @Autowired
    private AuthEventRepository authEventRepository;

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

    @Test
    void researchConsentRevoked_isAdmittedByTheWidenedCheckConstraint() {
        Instant occurredAt = Instant.now();
        AuthEventData data = new AuthEventData(
            AuthEvent.EventType.RESEARCH_CONSENT_REVOKED,
            AuthEvent.Result.SUCCESS,
            null,
            null,
            null,
            null,
            null,
            null,
            "{\"source\":\"SLACK_APP_HOME\",\"login\":\"octocat\"}"
        );

        // Before -15 this insert would violate ck_auth_event_event_type; after it, the row persists.
        assertThatCode(() ->
            authEventRepository.save(AuthEvent.create(data, 987654321L, occurredAt, "127.0.0.1", "test-agent"))
        ).doesNotThrowAnyException();

        assertThat(
            authEventRepository
                .findAll()
                .stream()
                .anyMatch(e -> e.getEventType() == AuthEvent.EventType.RESEARCH_CONSENT_REVOKED)
        ).isTrue();
    }
}
