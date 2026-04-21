package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Custom annotation to create a mock admin user for tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithUserSecurityContextFactory.class)
public @interface WithUser {
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
    String userId() default "testuser-user-id";

    /**
     * Value for the {@code github_id} identity claim. Defaults to {@code 1L} (testuser
     * seeded by {@code TestUserConfig}).
     */
    long githubId() default 1L;

    /**
     * Value for the {@code gitlab_id} identity claim. Defaults to {@code 0} (omitted).
     */
    long gitlabId() default 0L;
}
