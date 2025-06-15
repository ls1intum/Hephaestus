package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Custom annotation to create a mock admin user for tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(authorities = {"admin"})
public @interface WithAdminUser {
    
    /**
     * The username of the mock admin user.
     */
    String username() default "admin";
    
    /**
     * The user ID for the mock admin user.
     */
    String userId() default "admin-user-id";
}
