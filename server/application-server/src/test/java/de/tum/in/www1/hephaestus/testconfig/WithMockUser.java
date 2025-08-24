package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Custom annotation to create a mock authenticated user for tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockUserSecurityContextFactory.class)
public @interface WithMockUser {
    /**
     * The username of the mock user.
     */
    String username() default "testuser";

    /**
     * The authorities/roles for the mock user.
     */
    String[] authorities() default {};

    /**
     * The user ID for the mock user.
     */
    String userId() default "test-user-id";
}
