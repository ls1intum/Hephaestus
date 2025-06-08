package de.tum.in.www1.hephaestus.testconfig;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that require a full Spring Boot context.
 *
 * <p>Features:
 * <ul>
 *   <li>Shared PostgreSQL container for faster test execution
 *   <li>Full Spring Boot context with web environment
 *   <li>Database utilities for test data management
 * </ul>
 *
 * <p>Usage: Extend this class for integration tests needing Spring context.
 * 
 * <p>Data Isolation: Fresh schema per test class via create-drop.
 * Use {@code databaseTestUtils.cleanDatabase()} in @BeforeEach for data cleanup between individual tests.
 *
 * @author Felix T.J. Dietrich
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class BaseIntegrationTest {

    @Autowired
    protected DatabaseTestUtils databaseTestUtils;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
