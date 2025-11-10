package de.tum.in.www1.hephaestus.testconfig;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests requiring full Spring Boot context.
 * Uses shared PostgreSQL container for 60-75% faster execution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, GitHubIntegrationPostgresShutdown.class })
@Testcontainers
@Tag("integration")
public abstract class BaseIntegrationTest {

    @Autowired
    protected DatabaseTestUtils databaseTestUtils;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        var postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // HikariCP configuration optimized for tests to prevent connection warnings
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "300000"); // 5 minutes
        registry.add("spring.datasource.hikari.connection-timeout", () -> "10000"); // 10 seconds
        registry.add("spring.datasource.hikari.idle-timeout", () -> "60000"); // 1 minute
        registry.add("spring.datasource.hikari.validation-timeout", () -> "5000"); // 5 seconds
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "10000"); // 10 seconds
        registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");
    }
}
