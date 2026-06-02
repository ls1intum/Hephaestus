package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Authenticity guard for production-boot drift: builds the schema from the real Liquibase
 * migration set ({@code db/master.xml}) and runs Hibernate {@code ddl-auto: validate} over it,
 * exactly as a production boot does.
 *
 * <p>This closes a real gap: every other integration test extends {@code BaseIntegrationTest},
 * which builds the schema with Hibernate {@code ddl-auto: create} and disables Liquibase, so the
 * migrations are NEVER exercised in CI — a divergent migration (wrong column type, missing
 * constraint, entity/schema drift) only blows up at production boot. A clean context start here IS
 * the headline assertion; the explicit assertions below add concrete checks on top.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LiquibaseSchemaValidationIntegrationTest {

    /**
     * Fresh, dedicated container — deliberately NOT {@code PostgreSQLTestContainer.getInstance()}
     * and NOT {@code withReuse(true)}, so Liquibase always sees an empty database and builds the
     * schema from scratch. Lifecycle is bound to this class by {@code @Container} + JUnit's
     * {@code @Testcontainers} extension.
     */
    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_liquibase_validation")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideForProductionBootContract(DynamicPropertyRegistry registry) {
        // Point at the fresh, dedicated container.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // The crux of the test: flip the test-profile defaults back to the production boot contract.
        // application-test.yml sets liquibase.enabled=false and ddl-auto=create; @DynamicPropertySource
        // has the highest precedence, so these win.
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/master.xml");
        // Match the default (local/dev) boot's context so structural changesets apply and the
        // prod-only one-shot backfills stay gated out, mirroring application.yml's contexts: dev.
        registry.add("spring.liquibase.contexts", () -> "dev");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // A small pool is plenty for a boot-only validation context.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        // application-test.yml sets a 5s leak-detection threshold. Applying 600+ changesets holds a
        // single Liquibase connection well past that, producing a noisy (harmless) "connection leak"
        // stack trace on every run. Disable leak detection for this boot-only validation context.
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Production Liquibase schema applies cleanly and the JPA entities validate against it")
    void productionSchemaAppliesAndEntitiesValidate() {
        // Reaching this point means: (a) all Liquibase changesets applied to an empty DB, and
        // (b) Hibernate ddl-auto:validate found every @Entity mapping consistent with that schema.
        // The explicit assertions below make the test assert something concrete, not just
        // "context loaded".

        // (a) The migration ledger holds the full changeset set. 600+ in master.xml; assert a
        // conservative floor so the test stays robust to new changesets but still catches a
        // half-applied / empty ledger.
        Integer appliedChangesets = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM databasechangelog",
            Integer.class
        );
        assertThat(appliedChangesets)
            .as("Liquibase DATABASECHANGELOG ledger should record the full production migration set")
            .isNotNull()
            .isGreaterThan(500);

        // (b) Representative production columns exist in the Liquibase-built schema (not the
        // Hibernate-create schema). Query information_schema so we assert against the real catalog.
        assertColumnExists("workspace", "account_login");
        assertColumnExists("connection", "credentials_encrypted");
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
            Integer.class,
            table,
            column
        );
        assertThat(count)
            .as("Liquibase-built schema must contain column %s.%s", table, column)
            .isNotNull()
            .isEqualTo(1);
    }
}
