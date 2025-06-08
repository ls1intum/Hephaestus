package de.tum.in.www1.hephaestus.testconfig;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that require a full Spring Boot context.
 *
 * <p>This class provides:
 *
 * <ul>
 *   <li>PostgreSQL database via Testcontainers for realistic integration testing
 *   <li>Full Spring Boot context with web environment
 *   <li>Proper test profile configuration
 *   <li>JUnit 5 tagging for test categorization
 * </ul>
 *
 * <p>Usage: Extend this class for your integration tests that need full Spring context.
 *
 * <p>Note: Uses PostgreSQL to match the production environment and support all entity features.
 *
 * @author Felix T.J. Dietrich
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("hephaestus_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
