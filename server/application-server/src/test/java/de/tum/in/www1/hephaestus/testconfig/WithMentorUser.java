package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Custom annotation to create a mock mentor user for tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(authorities = {"mentor_access"})
public @interface WithMentorUser {
    
    /**
     * The username of the mock mentor user.
     */
    String username() default "mentor";
    
    /**
     * The user ID for the mock mentor user.
     */
    String userId() default "mentor-user-id";
}
