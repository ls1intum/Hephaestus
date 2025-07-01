package de.tum.in.www1.hephaestus.testconfig;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Base class for fast unit tests without Spring context.
 * Includes Mockito support for dependency mocking.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public abstract class BaseUnitTest {
    // Pure unit test base - no Spring context needed
}
