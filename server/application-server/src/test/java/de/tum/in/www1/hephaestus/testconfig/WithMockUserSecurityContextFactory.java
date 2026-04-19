package de.tum.in.www1.hephaestus.testconfig;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Factory to create a mock security context with JWT authentication for tests.
 */
public class WithMockUserSecurityContextFactory implements WithSecurityContextFactory<WithMockUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockUser annotation) {
        return MockSecurityContextUtils.createSecurityContext(
            annotation.username(),
            annotation.userId(),
            annotation.authorities(),
            "mock-token",
            annotation.githubId(),
            annotation.gitlabId()
        );
    }
}
