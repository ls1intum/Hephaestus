package de.tum.in.www1.hephaestus;

import de.tum.in.www1.hephaestus.testconfig.PostgreSQLTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test that verifies the Spring Boot application context can start successfully.
 * Tests basic configuration, bean wiring, and database connectivity.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class HephaestusApplicationTests {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = PostgreSQLTestContainer.getInstance();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void contextLoads() {
        // Verifies Spring context can start and all beans are properly configured
    }
}
