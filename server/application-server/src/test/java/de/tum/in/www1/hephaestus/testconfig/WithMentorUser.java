package de.tum.in.www1.hephaestus.testconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Custom annotation to create a mock mentor user for tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMentorUserSecurityContextFactory.class)
public @interface WithMentorUser {
    /**
     * The username of the mock mentor user.
     */
    String username() default "mentor";

    /**
     * The authorities/roles for the mock mentor user.
     */
    String[] authorities() default { "mentor_access" };

    /**
     * The user ID for the mock mentor user.
     */
    String userId() default "mentor-user-id";

    /**
     * Value for the {@code github_id} identity claim. Defaults to {@code 2L} (mentor seeded
     * by {@code TestUserConfig}).
     */
    long githubId() default 2L;

    /**
     * Value for the {@code gitlab_id} identity claim. Defaults to {@code 0} (omitted).
     */
    long gitlabId() default 0L;
}
