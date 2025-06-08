package de.tum.in.www1.hephaestus.testconfig;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Base class for pure unit tests without Spring context.
 *
 * <p>This class provides:
 *
 * <ul>
 *   <li>JUnit 5 tagging for test categorization
 *   <li>Fast test execution (no Spring context startup)
 *   <li>Mockito support for dependency injection
 *   <li>Common extensions for unit testing
 * </ul>
 *
 * <p>Usage: Extend this class for pure unit tests that don't need Spring Boot context.
 * Use BaseIntegrationTest for tests that need the full Spring application context.
 *
 * @author Felix T.J. Dietrich
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public abstract class BaseUnitTest {
    // Pure unit test base - no Spring context needed
}
