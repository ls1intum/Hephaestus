package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Custom annotation to create a mock admin user for tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithAdminUserSecurityContextFactory.class)
public @interface WithAdminUser {
    /**
     * The username of the mock admin user.
     */
    String username() default "admin";

    /**
     * The authorities/roles for the mock admin user.
     */
    String[] authorities() default { "admin" };

    /**
     * The user ID for the mock admin user.
     */
    String userId() default "admin-user-id";
}
