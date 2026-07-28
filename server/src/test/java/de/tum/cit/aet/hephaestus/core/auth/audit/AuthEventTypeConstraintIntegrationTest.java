package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.cit.aet.hephaestus.testconfig.GitHubIntegrationPostgresShutdown;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthDatasource;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Drift guard between {@link AuthEvent.EventType} and the {@code ck_auth_event_event_type} CHECK
 * constraint, against real (Liquibase-migrated) Postgres — the rest of the suite runs on
 * {@code ddl-auto: create}, which has no CHECK constraint, so a new enum value whose changelog
 * widening was forgotten would pass every other test and 500 in production on first use.
 *
 * <p>Parameterized over every enum value so the guard covers values added after this test, too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@Import(GitHubIntegrationPostgresShutdown.class)
@Testcontainers
@Tag("integration")
class AuthEventTypeConstraintIntegrationTest {

    // static: JUnit's per-method lifecycle gives each param case a fresh instance, so a non-static
    // counter would reset — uniqueness would rest entirely on the composite PK. Static makes ids unique too.
    private static final AtomicLong IDS = new AtomicLong(900_000_000L);

    @Autowired
    private AuthEventRepository authEventRepository;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        RealAuthDatasource.register(registry);
    }

    @ParameterizedTest
    @EnumSource(AuthEvent.EventType.class)
    void everyEventTypeIsAdmittedByTheCheckConstraint(AuthEvent.EventType type) {
        AuthEventData data = new AuthEventData(
            type,
            AuthEvent.Result.SUCCESS,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThatCode(() ->
            authEventRepository.save(AuthEvent.create(data, IDS.incrementAndGet(), Instant.now(), "127.0.0.1", "test"))
        ).doesNotThrowAnyException();
    }
}
