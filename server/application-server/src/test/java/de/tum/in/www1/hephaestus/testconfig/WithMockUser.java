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

    /**
     * Value for the {@code github_id} identity claim. Must match the seeded test user's
     * {@code native_id} so that {@link de.tum.in.www1.hephaestus.gitprovider.user.AuthenticatedUserService}
     * resolves to that row. Defaults to {@code 1L} (testuser seeded by TestUserConfig).
     * Set to {@code 0} to omit the claim and exercise the unauthenticated-identity path.
     */
    long githubId() default 1L;

    /**
     * Value for the {@code gitlab_id} identity claim. Defaults to {@code 0} (omitted).
     */
    long gitlabId() default 0L;
}
