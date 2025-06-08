package de.tum.in.www1.hephaestus.testconfig;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests that require a full Spring Boot context.
 *
 * <p>This class provides:
 *
 * <ul>
 *   <li>Embedded database for integration testing (consistent with existing setup)
 *   <li>Full Spring Boot context with web environment
 *   <li>Proper test profile configuration
 *   <li>JUnit 5 tagging for test categorization
 * </ul>
 *
 * <p>Usage: Extend this class for your integration tests that need full Spring context.
 *
 * <p>Note: Uses the same embedded database approach as the main application test.
 *
 * @author Felix T.J. Dietrich
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = DatabaseType.H2)
@Tag("integration")
public abstract class BaseIntegrationTest {
    // Configuration provided by annotations
    // Uses the same embedded database setup as HephaestusApplicationTests
}
